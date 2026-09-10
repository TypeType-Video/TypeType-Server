package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.readBilibiliRangeWithRetry
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.IOException
import java.io.OutputStream
import java.lang.management.ManagementFactory

class RetryingProxyInputStreamTest {
    private val request = Request.Builder().url("https://video.bilivideo.com/test.m4s")
        .header("Range", "bytes=0-").build()

    @Test
    fun `large range is read on demand without a body-sized allocation`() {
        val body = GeneratedBody(3L * 1024 * 1024 * 1024)
        val proxy = open { response(body) }
        assertEquals(0L, body.readBytes)
        assertEquals(body.length, proxy.contentLength)
        val bytes = ByteArray(64 * 1024)
        assertTrue(proxy.stream.read(bytes) > 0)
        assertTrue(body.readBytes <= bytes.size)
        proxy.close()
        assertTrue(body.closed)
    }

    @Test
    fun `truncated response replays verified prefix without duplicate bytes`() {
        val bodies = listOf(GeneratedBody(100_000, failAt = 20_000), GeneratedBody(100_000))
        var calls = 0
        val proxy = open { next ->
            assertEquals(request.header("Range"), next.header("Range"))
            response(bodies[calls++])
        }
        proxy.stream.use { assertArrayEquals(ByteArray(100_000) { 42 }, it.readBytes()) }
        assertEquals(2, calls)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun `strong etag permits replay without hashing all delivered media`() {
        val bodies = listOf(GeneratedBody(100, failAt = 20), GeneratedBody(100))
        var calls = 0
        val proxy = open { response(bodies[calls++]).newBuilder().header("ETag", "\"stable\"").build() }
        proxy.stream.use { assertArrayEquals(ByteArray(100) { 42 }, it.readBytes()) }
        assertEquals(2, calls)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun `weak etag still requires identical replay bytes`() {
        val bodies = listOf(GeneratedBody(100, failAt = 20), GeneratedBody(100, value = 43))
        var calls = 0
        val proxy = open { response(bodies[calls++]).newBuilder().header("ETag", "W/\"stable\"").build() }
        proxy.stream.use {
            assertEquals(20, it.read(ByteArray(100)))
            assertThrows(IOException::class.java) { it.read(ByteArray(100)) }
        }
        assertEquals(2, calls)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun `changed replay prefix is rejected before any new bytes are sent`() {
        val bodies = listOf(GeneratedBody(100, failAt = 20), GeneratedBody(100, value = 43))
        var calls = 0
        val proxy = open { response(bodies[calls++]) }
        proxy.stream.use {
            assertEquals(20, it.read(ByteArray(100)))
            assertThrows(IOException::class.java) { it.read(ByteArray(100)) }
        }
        assertEquals(2, calls)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun `changed response metadata is rejected and closed`() {
        val bodies = listOf(GeneratedBody(100, failAt = 20), GeneratedBody(100))
        var calls = 0
        val proxy = open {
            response(bodies[calls++]).newBuilder().header("ETag", "\"$calls\"").build()
        }
        proxy.stream.use {
            assertEquals(20, it.read(ByteArray(100)))
            assertThrows(IOException::class.java) { it.read(ByteArray(100)) }
        }
        assertEquals(2, calls)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun `retry budget includes transport errors and replay failures`() {
        var calls = 0
        val bodies = mutableListOf<GeneratedBody>()
        val proxy = open {
            calls++
            if (calls == 1) throw IOException("connect failed")
            val body = GeneratedBody(100, failAt = if (calls == 2) 20 else 10)
            bodies += body
            response(body)
        }
        proxy.stream.use {
            assertEquals(20, it.read(ByteArray(100)))
            assertThrows(IOException::class.java) { it.read(ByteArray(100)) }
        }
        assertEquals(3, calls)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun `close prevents further reads and retries`() {
        val body = GeneratedBody(100)
        var calls = 0
        val proxy = open { calls++; response(body) }
        proxy.close()
        proxy.close()
        assertThrows(IOException::class.java) { proxy.stream.read() }
        assertEquals(1, calls)
        assertTrue(body.closed)
    }

    @Test
    fun `temporary HTTP error during replay consumes the same bounded retry budget`() {
        val bodies = listOf(GeneratedBody(100, failAt = 20), GeneratedBody(0), GeneratedBody(100))
        var calls = 0
        val proxy = open {
            val result = response(bodies[calls++])
            if (calls == 2) result.newBuilder().code(503).build() else result
        }
        proxy.stream.use { assertArrayEquals(ByteArray(100) { 42 }, it.readBytes()) }
        assertEquals(3, calls)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun `cancellation during replay closes both responses without retrying`() {
        val bodies = listOf(GeneratedBody(100, failAt = 20), GeneratedBody(100))
        var active = true
        var calls = 0
        val proxy = open(checkActive = { if (!active) throw CancellationException("cancelled") }) {
            if (calls == 1) active = false
            response(bodies[calls++])
        }
        proxy.stream.use {
            assertEquals(20, it.read(ByteArray(100)))
            assertThrows(CancellationException::class.java) { it.read(ByteArray(100)) }
        }
        assertEquals(2, calls)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun `premature EOF is retried and the replay digest survives another failure`() {
        val bodies = listOf(
            GeneratedBody(100, failAt = 20, prematureEof = true),
            GeneratedBody(100, failAt = 40),
            GeneratedBody(100),
        )
        var calls = 0
        val proxy = open { response(bodies[calls++]) }
        proxy.stream.use { assertArrayEquals(ByteArray(100) { 42 }, it.readBytes()) }
        assertEquals(3, calls)
        assertTrue(bodies.all { it.closed })
    }

    @Test
    fun `unknown length supports EOF and zero length reads`() {
        val body = GeneratedBody(100, advertisedLength = -1)
        val proxy = open { response(body) }
        assertEquals(null, proxy.contentLength)
        proxy.stream.use {
            assertEquals(0, it.read(ByteArray(0)))
            assertArrayEquals(ByteArray(100) { 42 }, it.readBytes())
            assertEquals(-1, it.read())
        }
        assertTrue(body.closed)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TYPETYPE_PROXY_BENCHMARK", matches = "1")
    fun `compare eager body and streaming allocations on generated media`() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val thread = Thread.currentThread().threadId()
        fun measure(streaming: Boolean, size: Long, strongEtag: Boolean = false): Pair<Long, Long> {
            val allocated = bean.getThreadAllocatedBytes(thread)
            val start = System.nanoTime()
            val body = GeneratedBody(size)
            if (streaming) {
                open {
                    val result = response(body)
                    if (strongEtag) result.newBuilder().header("ETag", "\"stable\"").build() else result
                }.stream.use { it.copyTo(OutputStream.nullOutputStream(), 64 * 1024) }
            } else {
                response(body).use { it.body.bytes().inputStream().copyTo(OutputStream.nullOutputStream(), 64 * 1024) }
            }
            return (bean.getThreadAllocatedBytes(thread) - allocated) to (System.nanoTime() - start)
        }
        repeat(8) { measure(false, 1024 * 1024); measure(true, 1024 * 1024); measure(true, 1024 * 1024, true) }
        repeat(3) {
            val eager = measure(false, 64L * 1024 * 1024)
            val streaming = measure(true, 64L * 1024 * 1024)
            val validated = measure(true, 64L * 1024 * 1024, true)
            println("range64MiB eagerAllocated=${eager.first} streamingAllocated=${streaming.first} " +
                "etagAllocated=${validated.first} eagerNanos=${eager.second} streamingNanos=${streaming.second} " +
                "etagNanos=${validated.second}")
            assertTrue(streaming.first < 1024 * 1024, "Streaming must not allocate a range-sized body")
        }
    }

    private fun open(checkActive: () -> Unit = {}, execute: (Request) -> Response) =
        (readBilibiliRangeWithRetry(execute, request, checkActive) as ExtractionResult.Success).data

    private fun response(body: GeneratedBody) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(206).message("Partial Content")
        .header("Content-Type", "video/mp4")
        .header("Content-Range", "bytes 0-${body.length - 1}/${body.length}")
        .body(body).build()

    private class GeneratedBody(
        val length: Long,
        private val failAt: Long = Long.MAX_VALUE,
        private val value: Byte = 42,
        private val prematureEof: Boolean = false,
        private val advertisedLength: Long = length,
    ) : ResponseBody() {
        var readBytes = 0L
        var closed = false
        private val bytes = ByteArray(8192) { value }
        private val input = object : Source {
            override fun timeout() = Timeout.NONE
            override fun close() { closed = true }
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (readBytes >= failAt) {
                    if (prematureEof) return -1
                    throw IOException("connection reset")
                }
                if (readBytes == length) return -1
                val count = minOf(byteCount, bytes.size.toLong(), length - readBytes, failAt - readBytes).toInt()
                sink.write(bytes, 0, count)
                readBytes += count
                return count.toLong()
            }
        }.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength() = advertisedLength
        override fun source() = input
    }
}
