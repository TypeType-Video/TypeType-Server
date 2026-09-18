package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.OkHttpProxyService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Dns
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
