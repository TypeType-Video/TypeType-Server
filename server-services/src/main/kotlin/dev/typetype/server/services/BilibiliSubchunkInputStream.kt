package dev.typetype.server.services

import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Objects
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BilibiliSubchunkInputStream(
    private val execute: (Request) -> Response,
    private val baseRequest: Request,
    private val subRanges: List<ByteRange>,
    firstResponse: Response,
    private val attemptsPerChunk: Int = 3,
    private val checkActive: () -> Unit = {},
    private val alternateUrls: List<String> = emptyList(),
    private val enablePrefetch: Boolean = true,
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
        resumeUrls = alternateUrls,
    )
    private var closed = false
    private val singleByte = ByteArray(1)

    private var prefetchedIndex = -1
    private var prefetchFuture: CompletableFuture<InputStream>? = null

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
                // Current subchunk completed (EOF), close it and advance
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
        if (index == prefetchedIndex && prefetchFuture != null) {
            val future = prefetchFuture
            prefetchedIndex = -1
            prefetchFuture = null
            try {
                return future!!.get()
            } catch (_: Exception) {
                // Prefetch failed or cancelled; fallback to on-demand openSubchunk
            }
        }
        return openSubchunk(index)
    }

    private fun triggerPrefetch(targetIndex: Int) {
        if (targetIndex >= subRanges.size || closed) return
        prefetchedIndex = targetIndex
        prefetchFuture = CompletableFuture.supplyAsync({
            if (closed || Thread.currentThread().isInterrupted) {
                throw IOException("Stream closed before prefetch")
            }
            checkActive()
            val opened = openSubchunk(targetIndex)
            val buffer = ByteArrayOutputStream()
            val temp = ByteArray(32 * 1024)
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
            ByteArrayInputStream(buffer.toByteArray())
        }, prefetchExecutor)
    }

    private fun openSubchunk(index: Int): InputStream {
        val range = subRanges[index]
        val mirrors = listOf(baseRequest.url.toString()) + alternateUrls
        var lastError: IOException? = null
        for (attempt in 1..attemptsPerChunk) {
            checkActive()
            if (Thread.currentThread().isInterrupted) {
                throw lastError ?: IOException("Interrupted while opening subchunk")
            }
            val currentUrl = mirrors[(attempt - 1) % mirrors.size]
            val currentRequest = baseRequest.newBuilder()
                .url(currentUrl)
                .header("Range", "bytes=${range.start}-${range.end}")
                .build()
            val startMs = System.currentTimeMillis()
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
            BilibiliMirrorSpeedTracker.recordSuccess(currentUrl, System.currentTimeMillis() - startMs)
            val remainingMirrors = mirrors.filter { it != currentUrl }
            return RetryingProxyInputStream(
                execute = execute,
                request = currentRequest,
                initialResponse = response,
                attemptsRemaining = attemptsPerChunk - attempt,
                checkActive = checkActive,
                resumeUrls = remainingMirrors,
            )
        }
        throw lastError ?: IOException("Failed to open subchunk after $attemptsPerChunk attempts")
    }

    override fun close() {
        if (!closed) {
            closed = true
            prefetchFuture?.cancel(true)
            prefetchFuture = null
            currentStream?.close()
            currentStream = null
        }
    }

    companion object {
        private val prefetchExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "bilibili-subchunk-prefetch").apply {
                isDaemon = true
            }
        }
    }
}
