package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.YoutubeSessionCompleteRequest
import dev.typetype.server.services.SignedHlsManifestTokenResult
import dev.typetype.server.services.SignedHlsManifestTokenService
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YoutubeSessionCompleteResult
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionService
import dev.typetype.server.services.YoutubeSessionStreamService
import dev.typetype.server.services.YOUTUBE_SESSION_RECONNECT_CODE
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class YoutubeSessionStreamServiceTest {
    private val tokenService = SignedHlsManifestTokenService(
        "test-youtube-session-key-32-bytes",
        nowMillis = { 1_000L },
    )
    private val youtubeSessionService = YoutubeSessionService(
        YoutubeSessionCrypto.fromSecret("test-youtube-session-key-32-bytes")
    )

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `non YouTube url does not use authenticated extraction`() = runBlocking {
        val service = testService(failingStreamService())
        assertNull(service.getStreamInfo(TEST_USER_ID, "https://example.com/watch?v=test"))
    }

    @Test
    fun `generic authenticated extraction failure keeps session connected`() = runBlocking {
        connectYoutubeSession()
        val service = YoutubeSessionStreamService(
            failingStreamService("No suitable stream"),
            youtubeSessionService,
            FakeCacheService(),
            tokenService,
        )
        val result = service.getStreamInfo(TEST_USER_ID, "https://youtube.com/watch?v=test")
        assertTrue(result is ExtractionResult.Failure)
        assertEquals("connected", youtubeSessionService.status(TEST_USER_ID).status)
    }

    @Test
    fun `explicit session rejection marks session needs reconnect`() = runBlocking {
        connectYoutubeSession()
        val service = testService(
            failingStreamService(
                "rejected",
                ExtractionFailureKind.YoutubeSessionRejected,
            ),
        )

        val result = service.getStreamInfo(TEST_USER_ID, "https://youtube.com/watch?v=test")

        assertTrue(result is ExtractionResult.BadRequest)
        assertEquals(YOUTUBE_SESSION_RECONNECT_CODE, (result as ExtractionResult.BadRequest).code)
        assertEquals("needs_reconnect", youtubeSessionService.status(TEST_USER_ID).status)
    }

    @Test
    fun `successful authenticated extraction is cached by session`() = runBlocking {
        connectYoutubeSession()
        var calls = 0
        val stream = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                calls += 1
                return ExtractionResult.Success(testStreamResponse())
            }
        }
        val service = testService(stream)
        assertTrue(service.getStreamInfo(TEST_USER_ID, "https://youtube.com/watch?v=test") is ExtractionResult.Success)
        assertTrue(service.getStreamInfo(TEST_USER_ID, "https://youtube.com/watch?v=test") is ExtractionResult.Success)
        assertEquals(1, calls)
    }

    @Test
    fun `successful authenticated extraction signs hls url`() = runBlocking {
        connectYoutubeSession()
        val stream = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
                ExtractionResult.Success(
                    testStreamResponse().copy(
                        hlsUrl = "https://manifest.googlevideo.com/api/manifest/hls_variant/file/index.m3u8"
                    )
                )
        }
        val result = testService(stream).getStreamInfo(TEST_USER_ID, "https://youtube.com/watch?v=test")
        assertTrue(result is ExtractionResult.Success)
        val hlsUrl = (result as ExtractionResult.Success).data.hlsUrl
        assertTrue(hlsUrl.startsWith("/streams/hls-manifest?token="))
        val token = hlsUrl.substringAfter("token=")
        val verified = tokenService.verify(token)
        assertTrue(verified is SignedHlsManifestTokenResult.Valid)
        assertEquals("https://youtube.com/watch?v=test", (verified as SignedHlsManifestTokenResult.Valid).token.videoUrl)
    }

    private suspend fun connectYoutubeSession() {
        val pairing = youtubeSessionService.createPairing(TEST_USER_ID)
        val result = youtubeSessionService.complete(
            YoutubeSessionCompleteRequest(
                code = pairing.code,
                cookies = "SID=secret-cookie; SAPISID=secret-sapisid",
                poToken = "secret-pot-value",
            )
        )
        assertEquals(YoutubeSessionCompleteResult.Completed, result)
    }

    private fun failingStreamService(
        message: String = "unexpected call",
        kind: ExtractionFailureKind = ExtractionFailureKind.Unknown,
    ): StreamService = object : StreamService {
        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
            ExtractionResult.Failure(message, kind = kind)
    }

    private fun testService(stream: StreamService): YoutubeSessionStreamService =
        YoutubeSessionStreamService(stream, youtubeSessionService, FakeCacheService(), tokenService)
}
