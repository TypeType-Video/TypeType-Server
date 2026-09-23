package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.OkHttpProxyService
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress

class OkHttpProxyServiceTest {
    @Test
    fun `live HLS segment URL containing m3u8 path preserves binary bytes`() = runTest {
        val mediaBytes = byteArrayOf(
            0x47, 0x40, 0x00, 0x30, 0xff.toByte(), 0x00, 0xff.toByte(), 0x10,
            0x80.toByte(), 0x7f, 0x00, 0x01,
        )
        val service = OkHttpProxyService(clientFor(mediaBytes))
        val result = service.pipe(
            "https://rr8---sn-n4g-jqbe6.googlevideo.com/videoplayback/playlist/index.m3u8/sq/42/file/seg.ts",
            rangeHeader = null,
            domandBid = null,
        )

        assertTrue(result is ExtractionResult.Success)
        val response = (result as ExtractionResult.Success).data
        try {
            assertEquals(200, response.status)
            assertArrayEquals(mediaBytes, response.stream.readBytes())
        } finally {
            response.close()
        }
    }

    @Test
    fun `m3u8 manifest path is still rewritten when upstream MIME is generic`() = runTest {
        val manifest = "#EXTM3U\n#EXTINF:5.0,\nhttps://rr8---sn-n4g-jqbe6.googlevideo.com/segment.ts\n"
        val service = OkHttpProxyService(clientFor(manifest.toByteArray()))
        val result = service.pipe(
            "https://manifest.googlevideo.com/api/manifest/hls_variant/playlist/index.m3u8",
            rangeHeader = null,
            domandBid = null,
        )

        assertTrue(result is ExtractionResult.Success)
        val response = (result as ExtractionResult.Success).data
        try {
            assertTrue(response.stream.readBytes().decodeToString().contains("/proxy?url="))
        } finally {
            response.close()
        }
    }

    private fun clientFor(body: ByteArray): OkHttpClient = OkHttpClient.Builder()
        .dns(Dns { listOf(InetAddress.getByName("1.1.1.1")) })
        .addInterceptor(Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "application/octet-stream")
                .body(body.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        })
        .build()
}
