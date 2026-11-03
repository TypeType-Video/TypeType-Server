package dev.typetype.server.services

import kotlinx.coroutines.sync.Semaphore
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.Objects
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Semaphore as JavaSemaphore

class BilibiliSubchunkInputStream(
    private val execute: (Request) -> Response,
    private val baseRequest: Request,
    private val subRanges: List<ByteRange>,
    firstResponse: Response,
    private val attemptsPerChunk: Int = 3,
    private val checkActive: () -> Unit = {},
    private val alternateUrls: List<String> = emptyList(),
    private val mirrorExecute: ((Request) -> Response)? = null,
    private val mirrorPermits: Semaphore? = null,
    private val streamReadTimeoutMillis: Int = 10_000,
    private val nanoTime: () -> Long = System::nanoTime,
    firstOpenDurationNanos: Long = 0L,
    private val enablePrefetch: Boolean = BilibiliCdnConfig.isPrefetchEnabled(),
) : InputStream() {
    init {
        require(subRanges.isNotEmpty()) { "subRanges must not be empty" }
    }

    private var currentIndex = 0
    private var currentStream: InputStream? = RetryingProxyInputStream(
        execute = execute,
        request = baseRequest.newBuilder()
            .header("Range", "bytes=${subRanges[0].start}-${subRanges[0].end}")
            .build(),
        initialResponse = firstResponse,
        attemptsRemaining = (attemptsPerChunk - 1).coerceAtLeast(0),
        checkActive = checkActive,
        mirrorSwitcher = buildMirrorSwitcher(alternateUrls),
        initialOpenNanos = firstOpenDurationNanos,
        nanoTime = nanoTime,
        resumeUrls = alternateUrls,
    )
    @Volatile
    private var closed = false
    private val singleByte = ByteArray(1)

    private var prefetchedIndex = -1
    private var prefetchFuture: Future<InputStream>? = null
    private val activePrefetchStream = AtomicReference<Closeable?>(null)
    private val holdingPrefetchPermit = AtomicBoolean(false)

    init {
        if (enablePrefetch && subRanges.size > 1) {
            triggerPrefetch(1)
        }
    }

    override fun read(): Int = if (read(singleByte, 0, 1) == -1) -1 else singleByte[0].toInt() and 0xff

    override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
        Objects.checkFromIndexSize(offset, count, bytes.size)
        if (closed) throw IOException("Stream closed")
        if (count == 0) return 0

        while (true) {
            checkActive()
            val stream = currentStream ?: return -1
            val readBytes = stream.read(bytes, offset, count)
            if (readBytes > 0) {
                return readBytes
            }
            if (readBytes == -1) {
                stream.close()
                currentStream = null
                currentIndex++
                if (currentIndex >= subRanges.size) {
                    return -1
                }
                currentStream = obtainSubchunkStream(currentIndex)
                if (enablePrefetch) {
                    triggerPrefetch(currentIndex + 1)
                }
                continue
            }
            return 0
        }
    }

    private fun obtainSubchunkStream(index: Int): InputStream {
        val future = prefetchFuture?.takeIf { index == prefetchedIndex }
        if (future == null) prefetchFuture?.cancel(true)
        prefetchedIndex = -1
        prefetchFuture = null
        val prefetched = future?.let(::prefetchedStream)
        releasePrefetchPermit()
        return prefetched ?: openSubchunk(index)
    }

    private fun prefetchedStream(future: Future<InputStream>): InputStream? =
        try {
            future.get()
        } catch (_: ExecutionException) {
            null
        } catch (_: CancellationException) {
            null
        }

    private fun triggerPrefetch(targetIndex: Int) {
        if (targetIndex >= subRanges.size || closed) return
        releasePrefetchPermit()
        if (!prefetchPermits.tryAcquire()) return
        holdingPrefetchPermit.set(true)
        prefetchedIndex = targetIndex
        try {
            prefetchFuture = prefetchExecutor.submit(Callable {
                if (closed || Thread.currentThread().isInterrupted) {
                    throw IOException("Stream closed before prefetch")
                }
                checkActive()
                val opened = openSubchunk(targetIndex, activePrefetchStream)
                val buffer = ByteArrayOutputStream()
                val temp = ByteArray(32 * 1024)
                try {
                    opened.use { input ->
                        while (true) {
                            if (closed || Thread.currentThread().isInterrupted) {
                                throw IOException("Stream closed during prefetch")
                            }
                            val read = input.read(temp)
                            if (read < 0) break
                            buffer.write(temp, 0, read)
                        }
                    }
                } finally {
                    activePrefetchStream.compareAndSet(opened, null)
                }
                ByteArrayInputStream(buffer.toByteArray())
            })
        } catch (_: RejectedExecutionException) {
            prefetchedIndex = -1
            releasePrefetchPermit()
        }
    }

    private fun openSubchunk(
        index: Int,
        trackForClose: AtomicReference<Closeable?>? = null,
    ): InputStream {
        val range = subRanges[index]
        val mirrors = listOf(baseRequest.url.toString()) + alternateUrls
        var lastError: IOException? = null
        for (attempt in 1..attemptsPerChunk) {
            checkActive()
            if (closed || Thread.currentThread().isInterrupted) {
                throw lastError ?: IOException("Interrupted while opening subchunk")
            }
            val currentUrl = mirrors[(attempt - 1) % mirrors.size]
            val currentRequest = baseRequest.newBuilder()
                .url(currentUrl)
                .header("Range", "bytes=${range.start}-${range.end}")
                .build()
            val openStarted = nanoTime()
            val response = try {
                execute(currentRequest)
            } catch (error: IOException) {
                BilibiliMirrorSpeedTracker.recordFailure(currentUrl)
                lastError = error
                continue
            }
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                BilibiliMirrorSpeedTracker.recordFailure(currentUrl)
                lastError = IOException("Upstream returned $code for subchunk bytes=${range.start}-${range.end}")
                continue
            }
            val openDurationNanos = (nanoTime() - openStarted).coerceAtLeast(0)
            BilibiliMirrorSpeedTracker.recordSuccess(currentUrl, openDurationNanos / 1_000_000)
            val remainingMirrors = mirrors.filter { it != currentUrl }
            val stream = RetryingProxyInputStream(
                execute = execute,
                request = currentRequest,
                initialResponse = response,
                attemptsRemaining = attemptsPerChunk - attempt,
                checkActive = checkActive,
                mirrorSwitcher = buildMirrorSwitcher(remainingMirrors),
                initialOpenNanos = openDurationNanos,
                nanoTime = nanoTime,
                resumeUrls = remainingMirrors,
            )
            if (trackForClose != null) {
                trackForClose.set(stream)
                if (closed) {
                    trackForClose.compareAndSet(stream, null)
                    stream.close()
                    throw IOException("Stream closed")
                }
            }
            return stream
        }
        throw lastError ?: IOException("Failed to open subchunk after $attemptsPerChunk attempts")
    }

    private fun buildMirrorSwitcher(otherMirrors: List<String>): BilibiliSlowMirrorSwitcher? =
        if (otherMirrors.isNotEmpty() && mirrorExecute != null && mirrorPermits != null) {
            BilibiliSlowMirrorSwitcher(
                mirrorExecute, otherMirrors, mirrorPermits, checkActive, nanoTime, streamReadTimeoutMillis,
            )
        } else {
            null
        }

    private fun releasePrefetchPermit() {
        if (holdingPrefetchPermit.compareAndSet(true, false)) {
            prefetchPermits.release()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        activePrefetchStream.getAndSet(null)?.runCatching { close() }
        prefetchFuture?.cancel(true)
        prefetchFuture = null
        releasePrefetchPermit()
        currentStream?.close()
        currentStream = null
    }

    companion object {
        private const val MAX_CONCURRENT_PREFETCHES = 4
        private val prefetchPermits = JavaSemaphore(MAX_CONCURRENT_PREFETCHES)
        private val prefetchExecutor: ExecutorService = ThreadPoolExecutor(
            0,
            MAX_CONCURRENT_PREFETCHES,
            60L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
        ) { runnable ->
            Thread(runnable, "bilibili-subchunk-prefetch").apply {
                isDaemon = true
            }
        }
    }
}
