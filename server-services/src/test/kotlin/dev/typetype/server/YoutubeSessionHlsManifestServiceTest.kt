package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.YoutubeSessionCompleteRequest
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.SignedHlsManifestTokenService
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YoutubeSessionCompleteResult
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionHlsManifestService
import dev.typetype.server.services.YoutubeSessionService
import dev.typetype.server.services.YoutubeSessionStreamService
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class YoutubeSessionHlsManifestServiceTest {
    private val sessionTokenService = SignedHlsManifestTokenService("test-youtube-session-key-32-bytes")
    private val publicTokenService = PublicHlsManifestTokenService("test-public-hls-key")
    private val youtubeSessionService = YoutubeSessionService(
        YoutubeSessionCrypto.fromSecret("test-youtube-session-key-32-bytes")
    )

    companion object {
        const val VIDEO_URL = "https://youtube.com/watch?v=test"
        const val MANIFEST_URL = "https://manifest.googlevideo.com/api/manifest/hls/test"
        const val CHILD_URL = "https://manifest.googlevideo.com/api/manifest/hls/child"
        val MANIFEST = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n$CHILD_URL"

        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `signed YouTube session HLS signs child manifests`() = runBlocking {
        connectYoutubeSession()
        val stream = YoutubeSessionStreamService(streamService(), youtubeSessionService, FakeCacheService(), sessionTokenService)
        val hls = HlsManifestService(streamService(), clientFor(MANIFEST), FakeCacheService(), publicTokenService::createPath)
        val service = YoutubeSessionHlsManifestService(youtubeSessionService, stream, hls, sessionTokenService)
        val token = sessionTokenService.createToken(TEST_USER_ID, VIDEO_URL, youtubeSessionService.connectedCredentials(TEST_USER_ID)!!.fingerprint)

        val result = service.hlsManifest(token)

        assertTrue(result is ExtractionResult.Success)
        val body = (result as ExtractionResult.Success).data
        assertTrue(body.contains("hls-manifest?token="))
        assertFalse(body.contains("hls-manifest?url="))
    }

    private suspend fun connectYoutubeSession(): Unit {
        val pairing = youtubeSessionService.createPairing(TEST_USER_ID)
        val result = youtubeSessionService.complete(
            YoutubeSessionCompleteRequest(
                pairing.code,
                cookies = "SID=secret-cookie; SAPISID=secret-sapisid",
                poToken = "secret-pot-value",
            )
        )
        assertEquals(YoutubeSessionCompleteResult.Completed, result)
    }

    private fun streamService(): StreamService = object : StreamService {
        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
            ExtractionResult.Success(testStreamResponse().copy(hlsUrl = MANIFEST_URL))
    }

    private fun clientFor(body: String): OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/vnd.apple.mpegurl".toMediaType())).build()
    }).build()

}
