package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.MAX_BILIBILI_SUBCHUNK_SIZE
import dev.typetype.server.services.OkHttpProxyService
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress

class BilibiliRangeProxyTest {

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
    fun `BiliBili range proxy splits 2_5MB request into sub-1MB chunks upstream`() = runBlocking {
        // Simulating a 2.5MB segment (2,500,000 bytes)
        val totalRequested = 2_500_000L
        val fullData = ByteArray(totalRequested.toInt()) { (it % 256).toByte() }

        val requestedRanges = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .dns(Dns { listOf(InetAddress.getByName("1.1.1.1")) })
            .addInterceptor { chain ->
                val request = chain.request()
                val rangeHeader = request.header("Range") ?: ""
                requestedRanges.add(rangeHeader)

                // Parse the sub-range
                val match = Regex("""bytes=(\d+)-(\d+)""").find(rangeHeader)
                requireNotNull(match) { "Expected sub-range header, got: $rangeHeader" }
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toInt()
                val subLen = end - start + 1

                // Strictly ensure every outgoing subchunk is <= 1MB (MAX_BILIBILI_SUBCHUNK_SIZE)
                assertTrue(subLen <= MAX_BILIBILI_SUBCHUNK_SIZE, "Subchunk of size $subLen exceeds 1MB limit!")

                val subBytes = fullData.copyOfRange(start, end + 1)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Type", "video/mp4")
                    .header("Content-Range", "bytes $start-$end/10000000")
                    .header("Accept-Ranges", "bytes")
                    .body(subBytes.toResponseBody())
                    .build()
            }.build()

        val service = OkHttpProxyService(client)
        val result = service.pipe(
            url = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s",
            rangeHeader = "bytes=0-${totalRequested - 1}",
            domandBid = null,
        )

        require(result is ExtractionResult.Success)
        val proxyResponse = result.data
        assertEquals(206, proxyResponse.status)
        assertEquals(totalRequested, proxyResponse.contentLength)
        assertEquals("bytes 0-${totalRequested - 1}/10000000", proxyResponse.contentRange)

        // Read the entire stream and verify data integrity
        val receivedData = proxyResponse.stream.use { it.readBytes() }
        assertArrayEquals(fullData, receivedData)

        // Verify that 3 subchunks were requested, and every single one is under 1MB
        assertEquals(3, requestedRanges.size)
        assertEquals("bytes=0-999999", requestedRanges[0])
        assertEquals("bytes=1000000-1999999", requestedRanges[1])
        assertEquals("bytes=2000000-2499999", requestedRanges[2])
    }

    @Test
    fun `BiliBili subchunk proxy retries when a subsequent subchunk fails`() = runBlocking {
        val totalRequested = 2_200_000L
        val fullData = ByteArray(totalRequested.toInt()) { ((it * 3) % 256).toByte() }

        var chunk2Attempts = 0
        val client = OkHttpClient.Builder()
            .dns(Dns { listOf(InetAddress.getByName("1.1.1.1")) })
            .addInterceptor { chain ->
                val request = chain.request()
                val rangeHeader = request.header("Range") ?: ""
                val match = Regex("""bytes=(\d+)-(\d+)""").find(rangeHeader)!!
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toInt()

                // Trigger network failure on first attempt of chunk 2
                if (start == 1_000_000) {
                    chunk2Attempts++
                    if (chunk2Attempts == 1) {
                        throw IOException("Connection reset on subchunk 2")
                    }
                }

                val subBytes = fullData.copyOfRange(start, end + 1)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Type", "video/mp4")
                    .header("Content-Range", "bytes $start-$end/10000000")
                    .body(subBytes.toResponseBody())
                    .build()
            }.build()

        val service = OkHttpProxyService(client)
        val result = service.pipe(
            url = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s",
            rangeHeader = "bytes=0-${totalRequested - 1}",
            domandBid = null,
        )

        require(result is ExtractionResult.Success)
        val receivedData = result.data.stream.use { it.readBytes() }
        assertArrayEquals(fullData, receivedData)
        assertEquals(2, chunk2Attempts, "Subchunk 2 should have succeeded on retry")
    }

    @Test
    fun `BiliBili subchunk proxy does not fetch remaining subchunks if stream is closed early`() = runBlocking {
        val totalRequested = 3_000_000L
        val requestedRanges = mutableListOf<String>()

        val client = OkHttpClient.Builder()
            .dns(Dns { listOf(InetAddress.getByName("1.1.1.1")) })
            .addInterceptor { chain ->
                val request = chain.request()
                val rangeHeader = request.header("Range") ?: ""
                requestedRanges.add(rangeHeader)
                val dummy = ByteArray(1_000_000)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Type", "video/mp4")
                    .header("Content-Range", "bytes 0-999999/10000000")
                    .body(dummy.toResponseBody())
                    .build()
            }.build()

        val service = OkHttpProxyService(client)
        val result = service.pipe(
            url = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s",
            rangeHeader = "bytes=0-${totalRequested - 1}",
            domandBid = null,
        )

        require(result is ExtractionResult.Success)
        // Read only 100 bytes then close immediately (simulating player seek / abort)
        result.data.stream.use { stream ->
            val buf = ByteArray(100)
            val count = stream.read(buf)
            assertEquals(100, count)
        }

        // Only the first subchunk should have been requested!
        assertEquals(1, requestedRanges.size)
        assertEquals("bytes=0-999999", requestedRanges[0])
    }
}
