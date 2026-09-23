package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.routes.manifestRoutes
import dev.typetype.server.services.CachedManifestService
import dev.typetype.server.services.CachedNativeManifestService
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.NativeManifestService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManifestHlsPublicTokenRoutesTest {
    private var now = 1_000L
    private val tokenService = PublicHlsManifestTokenService("test-secret", nowMillis = { now })
    private val cache = InMemoryPublicHlsCache()

    @Test
    fun `signed public HLS manifest loads without bearer and signs child manifests`() = testApplication {
        val hlsService = HlsManifestService(NoopPublicHlsStreamService, clientFor(MANIFEST), cache, tokenService::createPath)
        application {
            install(ContentNegotiation) { json() }
            routing { manifestRoutes(manifest(), native(), hlsService, publicHlsManifestTokenService = tokenService) }
        }
        val token = tokenService.createToken(MANIFEST_URL)

        val response = client.get("/streams/hls-manifest?token=$token")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("hls-manifest?token="))
        assertEquals(2, Regex("hls-manifest\\?token=").findAll(body).count())
        assertTrue(body.contains("../proxy?url="))
        assertFalse(body.contains("hls-manifest?url="))

        val childToken = Regex("hls-manifest\\?token=([A-Za-z0-9._-]+)")
            .find(body)?.groupValues?.get(1)
        assertTrue(childToken != null)
        now += 30 * 60 * 1_000L

        val childResponse = client.get("/streams/hls-manifest?token=$childToken")

        assertEquals(HttpStatusCode.OK, childResponse.status)
    }

    @Test
    fun `invalid public token still fails cleanly without bearer`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { manifestRoutes(manifest(), native(), hls(), publicHlsManifestTokenService = tokenService) }
        }

        val response = client.get("/streams/hls-manifest?token=invalid")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    private fun clientFor(body: String): OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
            .build()
    }).build()

    private fun manifest() = CachedManifestService(ManifestService(NoopPublicHlsStreamService), cache)
    private fun native() = CachedNativeManifestService(NativeManifestService(), cache)
    private fun hls() = HlsManifestService(NoopPublicHlsStreamService, clientFor(MANIFEST), cache, tokenService::createPath)

    private companion object {
        const val MANIFEST_URL = "https://manifest.googlevideo.com/api/manifest/hls/test"
        const val CHILD_URL = "https://manifest.googlevideo.com/api/manifest/hls/child"
        const val IFRAME_CHILD_URL = "https://manifest.googlevideo.com/api/manifest/hls/iframe-child"
        const val SEGMENT_URL = "https://rr1---sn.googlevideo.com/videoplayback?id=1"
        val MANIFEST = "#EXTM3U\n#EXT-X-I-FRAME-STREAM-INF:URI=\"$IFRAME_CHILD_URL\"\n#EXT-X-STREAM-INF:BANDWIDTH=1\n$CHILD_URL\n$SEGMENT_URL"
    }
}

private object NoopPublicHlsStreamService : StreamService {
    override suspend fun getStreamInfo(url: String) = dev.typetype.server.models.ExtractionResult.Failure("unused")
}

private class InMemoryPublicHlsCache : CacheService {
    private val values = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = values[key]
    override suspend fun set(key: String, value: String, ttlSeconds: Long) { values[key] = value }
    override suspend fun delete(key: String) { values.remove(key) }
}
