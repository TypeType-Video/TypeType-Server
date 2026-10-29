package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.services.readBilibiliRangeWithRetry
import kotlinx.coroutines.sync.Semaphore
import okhttp3.MediaType.Companion.toMediaType
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BilibiliSlowMirrorSwitcherTest {
    @Test
    fun slowRangeResumesFromAlternateWithoutDuplicatingBytes() {
        val bytes = ByteArray(RANGE_SIZE) { (it % 251).toByte() }
        val clock = FakeClock()
        var slowRead = true
        val primaryBody = ByteArrayResponseBody(bytes) {
            if (slowRead) {
                slowRead = false
                clock.advanceMillis(2_000)
            }
        }
        val primaryRequest = request(PRIMARY_URL, "bytes=0-${bytes.lastIndex}")
        val alternateBody = ByteArrayResponseBody(bytes.copyOfRange(PREFIX_SIZE, bytes.size)) {
            clock.advanceMillis(10)
        }
        var alternateRequests = 0
        val proxy = open(
            primaryRequest,
            primaryBody,
            alternateUrls = listOf(ALTERNATE_URL),
            mirrorExecute = { alternateRequest ->
                alternateRequests++
                assertEquals("bytes=$PREFIX_SIZE-${bytes.lastIndex}", alternateRequest.header("Range"))
                assertEquals(STRONG_ETAG, alternateRequest.header("If-Range"))
                partialResponse(
                    alternateRequest,
                    alternateBody,
                    "bytes $PREFIX_SIZE-${bytes.lastIndex}/${bytes.size * 2}",
                )
            },
            nanoTime = clock::now,
        )

        val prefix = ByteArray(PREFIX_SIZE)
        assertEquals(prefix.size, readFully(proxy.stream, prefix))
        val remainder = proxy.stream.use { it.readBytes() }

        assertArrayEquals(bytes, prefix + remainder)
        assertEquals(1, alternateRequests)
        assertEquals(PREFIX_SIZE.toLong(), primaryBody.readBytes)
        assertTrue(primaryBody.closed)
        assertTrue(alternateBody.closed)
    }

    @Test
    fun mismatchedMirrorValidatorIsClosedAndPrimaryStreamContinues() {
        val bytes = ByteArray(RANGE_SIZE) { (it % 251).toByte() }
        val clock = FakeClock()
        var slowRead = true
        val primaryBody = ByteArrayResponseBody(bytes) {
            if (slowRead) {
                slowRead = false
                clock.advanceMillis(2_000)
            }
        }
        val alternateBody = ByteArrayResponseBody(bytes.copyOfRange(PREFIX_SIZE, bytes.size))
        val primaryRequest = request(PRIMARY_URL, "bytes=0-${bytes.lastIndex}")
        var alternateRequests = 0
        val proxy = open(
            primaryRequest,
            primaryBody,
            alternateUrls = listOf(ALTERNATE_URL),
            mirrorExecute = { alternateRequest ->
                alternateRequests++
                partialResponse(
                    alternateRequest,
                    alternateBody,
                    "bytes $PREFIX_SIZE-${bytes.lastIndex}/${bytes.size * 2}",
                    etag = "\"different\"",
                )
            },
            nanoTime = clock::now,
        )

        val prefix = ByteArray(PREFIX_SIZE)
        assertEquals(prefix.size, readFully(proxy.stream, prefix))
        val remainder = proxy.stream.use { it.readBytes() }

        assertArrayEquals(bytes, prefix + remainder)
        assertEquals(1, alternateRequests)
        assertTrue(alternateBody.closed)
        assertTrue(primaryBody.closed)
    }

    @Test
    fun healthyPrimaryDoesNotProbeAlternate() {
        val bytes = ByteArray(RANGE_SIZE) { (it % 251).toByte() }
        val clock = FakeClock()
        val body = ByteArrayResponseBody(bytes) { clock.advanceMillis(10) }
        val primaryRequest = request(PRIMARY_URL, "bytes=0-${bytes.lastIndex}")
        var alternateRequests = 0
        val proxy = open(
            primaryRequest,
            body,
            alternateUrls = listOf(ALTERNATE_URL),
            mirrorExecute = { alternateRequests++; throw AssertionError("unexpected mirror request") },
            nanoTime = clock::now,
        )

        assertArrayEquals(bytes, proxy.stream.use { it.readBytes() })
        assertEquals(0, alternateRequests)
    }

    @Test
    fun weakValidatorDoesNotProbeAlternate() {
        assertNoAlternateProbe("bytes=0-${RANGE_SIZE - 1}", "W/$STRONG_ETAG")
    }

    @Test
    fun overflowingRangeEndDoesNotBecomeAnOpenRange() {
        assertNoAlternateProbe("bytes=0-999999999999999999999999", STRONG_ETAG)
    }

    private fun assertNoAlternateProbe(range: String, etag: String) {
        val bytes = ByteArray(RANGE_SIZE) { (it % 251).toByte() }
        val clock = FakeClock()
        var slowRead = true
        val body = ByteArrayResponseBody(bytes) {
            if (slowRead) {
                slowRead = false
                clock.advanceMillis(2_000)
            }
        }
        var alternateRequests = 0
        val proxy = open(
            request(PRIMARY_URL, range),
            body,
            alternateUrls = listOf(ALTERNATE_URL),
            mirrorExecute = { alternateRequests++; throw AssertionError("unexpected mirror request") },
            nanoTime = clock::now,
            etag = etag,
        )

        assertArrayEquals(bytes, proxy.stream.use { it.readBytes() })
        assertEquals(0, alternateRequests)
    }
    private fun readFully(stream: java.io.InputStream, target: ByteArray): Int {
        var offset = 0
        while (offset < target.size) {
            val count = stream.read(target, offset, target.size - offset)
            if (count < 0) break
            offset += count
        }
        return offset
    }


    private fun open(
        request: Request,
        body: ByteArrayResponseBody,
        alternateUrls: List<String> = emptyList(),
        mirrorExecute: ((Request) -> Response)? = null,
        nanoTime: () -> Long = System::nanoTime,
        etag: String = STRONG_ETAG,
    ): ProxyResponse {
        val result = readBilibiliRangeWithRetry(
            execute = {
                partialResponse(request, body, "bytes 0-${body.contentLength() - 1}/${body.contentLength() * 2}", etag)
            },
            request = request,
            alternateUrls = alternateUrls,
            mirrorExecute = mirrorExecute,
            mirrorPermits = Semaphore(1),
            nanoTime = nanoTime,
        )
        return (result as ExtractionResult.Success).data
    }

    private fun request(url: String, range: String) =
        Request.Builder().url(url).header("Range", range).build()

    private fun partialResponse(
        request: Request,
        body: ByteArrayResponseBody,
        contentRange: String,
        etag: String = STRONG_ETAG,
    ) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(206)
        .message("Partial Content")
        .header("Content-Type", "video/mp4")
        .header("Content-Range", contentRange)
        .header("ETag", etag)
        .body(body)
        .build()

    private class FakeClock {
        private var current = 0L
        fun advanceMillis(value: Long) { current += value * 1_000_000 }
        fun now() = current
    }

    private class ByteArrayResponseBody(
        private val bytes: ByteArray,
        private val onRead: () -> Unit = {},
    ) : ResponseBody() {
        var readBytes = 0L
            private set
        var closed = false
            private set
        private val input = object : Source {
            override fun timeout() = Timeout.NONE
            override fun close() { closed = true }
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (byteCount == 0L) return 0
                if (readBytes == bytes.size.toLong()) return -1
                val count = minOf(byteCount, bytes.size - readBytes).toInt()
                sink.write(bytes, readBytes.toInt(), count)
                readBytes += count
                onRead()
                return count.toLong()
            }
        }.buffer()

        override fun contentType() = "video/mp4".toMediaType()
        override fun contentLength() = bytes.size.toLong()
        override fun source() = input
    }

    private companion object {
        const val RANGE_SIZE = 256 * 1024
        const val PREFIX_SIZE = 64 * 1024
        const val STRONG_ETAG = "\"stable\""
        const val PRIMARY_URL = "https://upos-hz-mirrorakam.akamaized.net/video.m4s"
        const val ALTERNATE_URL = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s"
    }
}
