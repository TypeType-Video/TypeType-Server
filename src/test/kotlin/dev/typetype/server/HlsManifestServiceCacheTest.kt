package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.StreamService
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress

class HlsManifestServiceCacheTest {
    @Test
    fun `hls manifests are cached briefly by manifest url`() = runTest {
        var calls = 0
        val client = proxyTestClient(Interceptor { chain ->
            calls += 1
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("#EXTM3U\nsegment.ts".toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
                .build()
        })
        val service = HlsManifestService(NoopStreamService, client, InMemoryCache())
        val url = "https://manifest.googlevideo.com/master.m3u8"

        service.hlsManifest(url)
        service.hlsManifest(url)

        assertEquals(1, calls)
    }

    @Test
    fun `provider manifests are fetched again instead of serving signed cache entries`() = runTest {
        var calls = 0
        val client = proxyTestClient(Interceptor { chain ->
            calls += 1
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("#EXTM3U\nsegment.m4s".toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
                .build()
        })
        val service = HlsManifestService(NoopStreamService, client, InMemoryCache())
        val url = "https://upos-hz-mirrorakam.akamaized.net/master.m3u8?deadline=2000000000"

        service.hlsManifest(url)
        service.hlsManifest(url)

        assertEquals(2, calls)
    }

    @Test
    fun `attested manifest is scoped to youtube live`() = runTest {
        val requestedUrls = mutableListOf<String>()
        val attestedVideoIds = mutableListOf<String>()
        val client = proxyTestClient(Interceptor { chain ->
            requestedUrls += chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("#EXTM3U".toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
                .build()
        })
        val streams = FixedStreamService(
            testStreamResponse(hlsUrl = "https://upos-hz-mirrorakam.akamaized.net/legacy.m3u8").copy(isLive = true),
        )
        val service = HlsManifestService(streams, client, attestedYoutubeHls = { videoId ->
            attestedVideoIds += videoId
            "https://manifest.googlevideo.com/attested.m3u8"
        })

        val publicResult = service.hlsManifest("https://youtube.com/watch?v=test-id")
        val sessionResult = service.hlsManifestFromStreamInfo(
            ExtractionResult.Success(testStreamResponse().copy(id = "session-id", isLive = true)),
        )
        val bilibiliResult = service.hlsManifest("https://www.bilibili.com/video/BV1xx411c7mD")

        assertEquals(ExtractionResult.Success("#EXTM3U"), publicResult)
        assertEquals(ExtractionResult.Success("#EXTM3U"), sessionResult)
        assertEquals(ExtractionResult.Success("#EXTM3U"), bilibiliResult)
        assertEquals(listOf("test-id", "session-id"), attestedVideoIds)
        assertEquals(
            listOf(
                "https://manifest.googlevideo.com/attested.m3u8",
                "https://manifest.googlevideo.com/attested.m3u8",
                "https://upos-hz-mirrorakam.akamaized.net/legacy.m3u8",
            ),
            requestedUrls,
        )
    }

    @Test
    fun `NicoNico manifests use signed cookie and proxy segments`() = runTest {
        val requests = mutableListOf<Pair<String, String?>>()
        val client = proxyTestClient(Interceptor { chain ->
            requests += chain.request().url.toString() to chain.request().header("Cookie")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("#EXTM3U\nsegment.cmfa".toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
                .build()
        })
        val service = HlsManifestService(NoopStreamService, client)

        val result = service.hlsManifest(
            "https://delivery.domand.nicovideo.jp/media/audio.m3u8?session=abc#cookie=domand_bid%3Dbid-value&length=1"
        )

        assertEquals(
            listOf("https://delivery.domand.nicovideo.jp/media/audio.m3u8?session=abc" to "domand_bid=bid-value"),
            requests,
        )
        assertTrue(result is ExtractionResult.Success)
        assertTrue((result as ExtractionResult.Success).data.contains("../proxy?url="))
        assertTrue(result.data.contains("domand_bid=bid-value"))
    }
}

private fun proxyTestClient(interceptor: Interceptor): OkHttpClient = OkHttpClient.Builder()
    .dns(Dns { listOf(InetAddress.getByName("1.1.1.1")) })
    .addInterceptor(interceptor)
    .build()

private object NoopStreamService : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        ExtractionResult.Failure("unused")
}

private class FixedStreamService(private val response: StreamResponse) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = ExtractionResult.Success(response)
}

private class InMemoryCache : CacheService {
    private val values = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = values[key]
    override suspend fun set(key: String, value: String, ttlSeconds: Long) { values[key] = value }
    override suspend fun delete(key: String) { values.remove(key) }
}
