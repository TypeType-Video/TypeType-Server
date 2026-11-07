package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.BilibiliMirrorSpeedTracker
import dev.typetype.server.services.MAX_BILIBILI_SUBCHUNK_SIZE
import dev.typetype.server.services.OkHttpProxyService
import dev.typetype.server.services.readBilibiliRangeWithRetry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import okhttp3.Dns
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BilibiliRangeProxyTest {
    @BeforeEach
    fun setup() {
        BilibiliMirrorSpeedTracker.reset()
    }

    @Test
    fun `BiliBili range proxy retries transport failures`() = runBlocking {
        var calls = 0
        val bytes = byteArrayOf(1, 2, 3, 4)
        val client = OkHttpClient.Builder()
            .dns(Dns { listOf(InetAddress.getByName("1.1.1.1")) })
            .addInterceptor { chain ->
                calls += 1
                val request = chain.request()
                assertEquals(OkHttpProxyService.BILIBILI_USER_AGENT, request.header("User-Agent"))
                assertEquals("https://www.bilibili.com", request.header("Referer"))
                assertEquals("*/*", request.header("Accept"))
                assertNull(request.header("Connection"))
                assertEquals("bytes=0-3", request.header("Range"))
                if (calls == 1) throw IOException("unexpected end of stream")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Type", "video/mp4")
                    .header("Content-Range", "bytes 0-3/4")
                    .body(bytes.toResponseBody())
                    .build()
            }.build()

        val service = OkHttpProxyService(client)
        val result = service.pipe(
            url = "https://upos-hz-mirrorakam.akamaized.net/video.m4s",
            rangeHeader = "bytes=0-3",
            domandBid = null,
        )

        require(result is ExtractionResult.Success)
        assertEquals(2, calls)
        assertEquals(206, result.data.status)
        assertEquals("bytes 0-3/4", result.data.contentRange)
        result.data.stream.use { assertArrayEquals(bytes, it.readBytes()) }
    }

    @Test
    fun `keeps direct range path by default when subchunk proxy is disabled`() {
        val requestedRanges = mutableListOf<String?>()
        val req = Request.Builder()
            .url("https://upos-sz-mirrorcos.bilivideo.com/video.m4s")
            .header("Range", "bytes=0-1999999")
            .build()
        val result = readBilibiliRangeWithRetry(
            execute = { r ->
                requestedRanges += r.header("Range")
                Response.Builder()
                    .request(r)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes 0-1999999/5000000")
                    .body(ByteArray(16) { 1 }.toResponseBody())
                    .build()
            },
            request = req,
            enableSubchunks = false,
        )
        require(result is ExtractionResult.Success)
        assertEquals(listOf("bytes=0-1999999"), requestedRanges)
        result.data.close()
    }

    @Test
    fun `splits multi-MB request into sub-1MB chunks and clamps Content-Range end to file size`() {
        val totalFileSize = 1_500_000L
        val fullData = ByteArray(totalFileSize.toInt()) { (it % 251).toByte() }
        val requestedRanges = mutableListOf<String>()
        val req = Request.Builder()
            .url("https://upos-sz-mirrorcosov.bilivideo.com/video.m4s")
            .header("Range", "bytes=0-2499999")
            .build()

        val result = readBilibiliRangeWithRetry(
            execute = { r ->
                val rangeHeader = r.header("Range").orEmpty()
                requestedRanges += rangeHeader
                val (s, e) = rangeHeader.removePrefix("bytes=").split('-').map(String::toInt)
                val actualEnd = minOf(e, totalFileSize.toInt() - 1)
                assertTrue(actualEnd - s + 1 <= MAX_BILIBILI_SUBCHUNK_SIZE)
                Response.Builder()
                    .request(r)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes $s-$actualEnd/$totalFileSize")
                    .body(fullData.copyOfRange(s, actualEnd + 1).toResponseBody())
                    .build()
            },
            request = req,
            enableSubchunks = true,
            enablePrefetch = false,
        )

        require(result is ExtractionResult.Success)
        assertEquals("bytes 0-1499999/1500000", result.data.contentRange)
        assertEquals(totalFileSize, result.data.contentLength)
        result.data.stream.use { assertArrayEquals(fullData, it.readBytes()) }
        assertEquals(listOf("bytes=0-999999", "bytes=1000000-1499999"), requestedRanges)
    }

    @Test
    fun `open-ended range request from zero seamlessly splits into subchunks`() {
        val totalFileSize = 1_500_000L
        val fullData = ByteArray(totalFileSize.toInt()) { (it % 251).toByte() }
        val requestedRanges = mutableListOf<String>()

        val req = Request.Builder()
            .url("https://upos-sz-mirrorcosov.bilivideo.com/video.m4s")
            .header("Range", "bytes=0-")
            .build()

        val result = readBilibiliRangeWithRetry(
            execute = { r ->
                val rangeHeader = r.header("Range").orEmpty()
                requestedRanges += rangeHeader
                val (s, e) = rangeHeader.removePrefix("bytes=").split('-').map(String::toInt)
                val actualEnd = minOf(e, totalFileSize.toInt() - 1)
                Response.Builder()
                    .request(r)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes $s-$actualEnd/$totalFileSize")
                    .body(fullData.copyOfRange(s, actualEnd + 1).toResponseBody())
                    .build()
            },
            request = req,
            enableSubchunks = true,
            enablePrefetch = false,
        )

        require(result is ExtractionResult.Success)
        assertEquals("bytes 0-1499999/1500000", result.data.contentRange)
        assertEquals(totalFileSize, result.data.contentLength)
        result.data.stream.use { assertArrayEquals(fullData, it.readBytes()) }
        assertEquals(listOf("bytes=0-999999", "bytes=1000000-1499999"), requestedRanges)
    }

    @Test
    fun `open-ended range request from seek position seamlessly splits into subchunks`() {
        val totalFileSize = 2_500_000L
        val fullData = ByteArray(totalFileSize.toInt()) { (it % 251).toByte() }
        val requestedRanges = mutableListOf<String>()

        val req = Request.Builder()
            .url("https://upos-sz-mirrorcosov.bilivideo.com/video.m4s")
            .header("Range", "bytes=1200000-")
            .build()

        val result = readBilibiliRangeWithRetry(
            execute = { r ->
                val rangeHeader = r.header("Range").orEmpty()
                requestedRanges += rangeHeader
                val (s, e) = rangeHeader.removePrefix("bytes=").split('-').map(String::toInt)
                val actualEnd = minOf(e, totalFileSize.toInt() - 1)
                Response.Builder()
                    .request(r)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes $s-$actualEnd/$totalFileSize")
                    .body(fullData.copyOfRange(s, actualEnd + 1).toResponseBody())
                    .build()
            },
            request = req,
            enableSubchunks = true,
            enablePrefetch = false,
        )

        require(result is ExtractionResult.Success)
        assertEquals("bytes 1200000-2499999/2500000", result.data.contentRange)
        assertEquals(1_300_000L, result.data.contentLength)
        val expectedData = fullData.copyOfRange(1200000, 2500000)
        result.data.stream.use { assertArrayEquals(expectedData, it.readBytes()) }
        assertEquals(listOf("bytes=1200000-2199999", "bytes=2200000-2499999"), requestedRanges)
    }

    @Test
    fun `mid-subchunk network failure seamlessly recovers remaining bytes on alternate mirror`() {
        val totalFileSize = 1_500_000L
        val fullData = ByteArray(totalFileSize.toInt()) { (it % 251).toByte() }
        val requestedRanges = mutableListOf<String>()
        val requestedUrls = mutableListOf<String>()
        var failedMidSubchunk = false

        val req = Request.Builder()
            .url("https://upos-sz-mirrorcosov.bilivideo.com/video.m4s")
            .header("Range", "bytes=0-1499999")
            .build()

        val result = readBilibiliRangeWithRetry(
            execute = { r ->
                val rangeHeader = r.header("Range").orEmpty()
                val url = r.url.toString()
                requestedRanges += rangeHeader
                requestedUrls += url
                val (s, e) = rangeHeader.removePrefix("bytes=").split('-').map(String::toInt)
                val actualEnd = minOf(e, totalFileSize.toInt() - 1)
                val chunkData = fullData.copyOfRange(s, actualEnd + 1)

                val body = if (rangeHeader == "bytes=0-999999" && !failedMidSubchunk) {
                    failedMidSubchunk = true
                    object : ResponseBody() {
                        private var delivered = 0
                        override fun contentType(): MediaType? = null
                        override fun contentLength(): Long = chunkData.size.toLong()
                        override fun source() = object : Source {
                            override fun timeout() = Timeout.NONE
                            override fun close() {}
                            override fun read(sink: Buffer, byteCount: Long): Long {
                                if (delivered >= 100_000) {
                                    throw IOException("Connection dropped mid-subchunk")
                                }
                                val toRead = minOf(byteCount, 100_000L - delivered).toInt()
                                sink.write(chunkData, delivered, toRead)
                                delivered += toRead
                                return toRead.toLong()
                            }
                        }.buffer()
                    }
                } else {
                    chunkData.toResponseBody()
                }

                Response.Builder()
                    .request(r)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes $s-$actualEnd/$totalFileSize")
                    .body(body)
                    .build()
            },
            request = req,
            alternateUrls = listOf("https://upos-sz-mirroraliov.bilivideo.com/video.m4s"),
            enableSubchunks = true,
            enablePrefetch = false,
        )

        require(result is ExtractionResult.Success)
        val readStreamBytes = result.data.stream.use { it.readBytes() }
        assertArrayEquals(fullData, readStreamBytes)
        assertTrue(failedMidSubchunk)
        assertTrue(requestedRanges.any { it.startsWith("bytes=83616-") || it.startsWith("bytes=100000-") })
        result.data.close()
    }

    @Test
    fun `falls back to primary URL when Akamai is blacklisted and no other mirrors exist`() {
        System.setProperty("BILIBILI_BLACKLIST_AKAMAI_CDN", "true")
        try {
            val req = Request.Builder()
                .url("https://custom-edge.akamaized.net/non-upos-path/video.m4s")
                .header("Range", "bytes=0-3")
                .build()
            val result = readBilibiliRangeWithRetry(
                execute = { r ->
                    Response.Builder()
                        .request(r)
                        .protocol(Protocol.HTTP_1_1)
                        .code(206)
                        .message("Partial Content")
                        .header("Content-Range", "bytes 0-3/4")
                        .body(byteArrayOf(7, 8, 9, 10).toResponseBody())
                        .build()
                },
                request = req,
            )
            require(result is ExtractionResult.Success)
            result.data.stream.use { assertArrayEquals(byteArrayOf(7, 8, 9, 10), it.readBytes()) }
        } finally {
            System.clearProperty("BILIBILI_BLACKLIST_AKAMAI_CDN")
        }
    }

    @Test
    fun `subchunk stream preserves slow mirror switcher and closes in-flight prefetch on close`() {
        val prefetchStarted = CountDownLatch(1)
        val prefetchClosed = AtomicBoolean(false)
        var mirrorProbed = false
        var now = 0L

        val req = Request.Builder()
            .url("https://upos-sz-mirrorcos.bilivideo.com/video.m4s")
            .header("Range", "bytes=0-1999999")
            .build()

        val result = readBilibiliRangeWithRetry(
            execute = { r ->
                val range = r.header("Range").orEmpty()
                if (range == "bytes=0-999999") {
                    now += 15_000_000_000L
                    Response.Builder()
                        .request(r)
                        .protocol(Protocol.HTTP_1_1)
                        .code(206)
                        .message("Partial Content")
                        .header("ETag", "\"v1\"")
                        .header("Content-Range", "bytes 0-999999/2000000")
                        .body(ByteArray(1_000_000) { 1 }.toResponseBody())
                        .build()
                } else {
                    val blockingBody = object : ResponseBody() {
                        private val src = object : Source {
                            override fun timeout() = Timeout.NONE
                            override fun close() {
                                prefetchClosed.set(true)
                            }
                            override fun read(sink: Buffer, byteCount: Long): Long {
                                prefetchStarted.countDown()
                                Thread.sleep(5_000)
                                return -1
                            }
                        }.buffer()
                        override fun contentType(): MediaType? = null
                        override fun contentLength(): Long = 1_000_000L
                        override fun source() = src
                    }
                    Response.Builder()
                        .request(r)
                        .protocol(Protocol.HTTP_1_1)
                        .code(206)
                        .message("Partial Content")
                        .header("Content-Range", "bytes 1000000-1999999/2000000")
                        .body(blockingBody)
                        .build()
                }
            },
            request = req,
            alternateUrls = listOf("https://upos-sz-mirrorali.bilivideo.com/video.m4s"),
            mirrorExecute = { r ->
                mirrorProbed = true
                Response.Builder()
                    .request(r)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes 0-999999/2000000")
                    .body(ByteArray(1_000_000) { 1 }.toResponseBody())
                    .build()
            },
            mirrorPermits = Semaphore(1),
            streamReadTimeoutMillis = 10_000,
            nanoTime = { now },
            enableSubchunks = true,
            enablePrefetch = true,
        )

        require(result is ExtractionResult.Success)
        assertTrue(prefetchStarted.await(2, TimeUnit.SECONDS))
        assertEquals(64 * 1024, result.data.stream.readNBytes(64 * 1024).size)
        assertTrue(result.data.stream.read() >= 0)
        assertTrue(mirrorProbed)
        result.data.close()
        assertTrue(prefetchClosed.get())
    }

    @Test
    fun `starts on the mirror measured faster than the listed one`() {
        val slow = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/v.m4s"
        val fast = "https://upos-sz-mirrorcosov.bilivideo.com/upgcxcode/v.m4s"
        BilibiliMirrorSpeedTracker.recordThroughput(slow, 117_000.0)
        BilibiliMirrorSpeedTracker.recordThroughput(fast, 1_600_000.0)
        val hosts = mutableListOf<String>()
        val execute: (Request) -> Response = { request ->
            hosts += request.url.host
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(206)
                .message("Partial Content")
                .header("Content-Type", "video/mp4")
                .header("Content-Range", "bytes 0-3/100")
                .body(byteArrayOf(1, 2, 3, 4).toResponseBody())
                .build()
        }
        val request = Request.Builder().url(slow).header("Range", "bytes=0-3").build()

        val result = readBilibiliRangeWithRetry(
            execute,
            request,
            alternateUrls = listOf(fast),
            cdnRegion = null,
            enableSubchunks = false,
            enablePrefetch = false,
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(listOf("upos-sz-mirrorcosov.bilivideo.com"), hosts)
    }

    @Test
    fun `opportunistically probes unmeasured alternate mirror during subchunk prefetch when primary is not fast`() {
        BilibiliMirrorSpeedTracker.reset()
        val primary = "https://upos-sz-mirrorcos.bilivideo.com/video.m4s"
        val alternate = "https://upos-sz-mirrorali.bilivideo.com/video.m4s"
        val requestedHosts = java.util.Collections.synchronizedList(mutableListOf<String>())

        val req = Request.Builder()
            .url(primary)
            .header("Range", "bytes=0-1999999")
            .build()

        val result = readBilibiliRangeWithRetry(
            execute = { r ->
                requestedHosts += r.url.host
                val range = r.header("Range").orEmpty()
                val total = 2_000_000L
                val (start, end) = range.removePrefix("bytes=").split('-').map(String::toLong)
                val len = (end - start + 1).toInt()
                Response.Builder()
                    .request(r)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes $start-$end/$total")
                    .body(ByteArray(len) { 42 }.toResponseBody())
                    .build()
            },
            request = req,
            alternateUrls = listOf(alternate),
            cdnRegion = null,
            enableSubchunks = true,
            enablePrefetch = true,
        )

        assertTrue(result is ExtractionResult.Success)
        val data = (result as ExtractionResult.Success).data
        val buf = ByteArray(1024 * 1024)
        var totalRead = 0
        data.stream.use { s ->
            while (true) {
                val n = s.read(buf)
                if (n == -1) break
                totalRead += n
            }
        }
        assertEquals(2_000_000, totalRead)
        assertTrue(requestedHosts.contains("upos-sz-mirrorcos.bilivideo.com"))
        assertTrue(requestedHosts.contains("upos-sz-mirrorali.bilivideo.com"))
    }

    @Test
    fun `opportunistically probes alternate mirror but penalizes candidate when alternate fails or times out`() {
        BilibiliMirrorSpeedTracker.reset()
        val primary = "https://upos-sz-mirrorcos.bilivideo.com/video.m4s"
        val alternate = "https://upos-sz-mirrorali.bilivideo.com/video.m4s"

        val req = Request.Builder()
            .url(primary)
            .header("Range", "bytes=0-1999999")
            .build()

        val result = readBilibiliRangeWithRetry(
            execute = { r ->
                if (r.url.host == "upos-sz-mirrorali.bilivideo.com") {
                    throw IOException("Alternate mirror timeout")
                }
                val range = r.header("Range").orEmpty()
                val total = 2_000_000L
                val (start, end) = range.removePrefix("bytes=").split('-').map(String::toLong)
                val len = (end - start + 1).toInt()
                Response.Builder()
                    .request(r)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes $start-$end/$total")
                    .body(ByteArray(len) { 42 }.toResponseBody())
                    .build()
            },
            request = req,
            alternateUrls = listOf(alternate),
            cdnRegion = null,
            enableSubchunks = true,
            enablePrefetch = true,
        )

        assertTrue(result is ExtractionResult.Success)
        val data = (result as ExtractionResult.Success).data
        val buf = ByteArray(1024 * 1024)
        var totalRead = 0
        data.stream.use { s ->
            while (true) {
                val n = s.read(buf)
                if (n == -1) break
                totalRead += n
            }
        }
        assertEquals(2_000_000, totalRead)
        val candidate = BilibiliMirrorSpeedTracker.findProbeCandidate(listOf(primary, alternate), primary)
        org.junit.jupiter.api.Assertions.assertNull(candidate)
    }
}
