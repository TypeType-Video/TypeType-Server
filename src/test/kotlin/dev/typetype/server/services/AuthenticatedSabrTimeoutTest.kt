package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import dev.typetype.server.sabr.YoutubeSabrInfo

class AuthenticatedSabrTimeoutTest {
    @Test
    fun `visitor data timeout returns a typed failure`() = runBlocking {
        val service = service(visitor = { blockingCall() })

        val result = withTimeout(4_000L) { service.fetch(USER_ID, VIDEO_ID) }

        assertEquals(AuthenticatedSabrInfoResult.TimedOut, result)
    }

    @Test
    fun `token timeout returns a typed failure`() = runBlocking {
        val service = service(token = { _, _, _ -> blockingCall() })

        val result = withTimeout(4_000L) { service.fetch(USER_ID, VIDEO_ID) }

        assertEquals(AuthenticatedSabrInfoResult.TimedOut, result)
    }

    @Test
    fun `probe timeout returns a typed failure`() = runBlocking {
        val service = service(probe = { _, _ -> blockingCall() })

        val result = withTimeout(4_000L) { service.fetch(USER_ID, VIDEO_ID) }

        assertEquals(AuthenticatedSabrInfoResult.TimedOut, result)
    }

    @Test
    fun `timeout releases credentials for the next request`() = runBlocking {
        var calls = 0
        val service = service(visitor = {
            calls++
            if (calls == 1) blockingCall() else SESSION_BINDING
        })

        val timedOut = withTimeout(4_000L) { service.fetch(USER_ID, VIDEO_ID) }
        val recovered = withTimeout(4_000L) { service.fetch(USER_ID, VIDEO_ID) }

        assertEquals(AuthenticatedSabrInfoResult.TimedOut, timedOut)
        assertEquals(2, calls)
        assertTrue(recovered is AuthenticatedSabrInfoResult.Ready, recovered.toString())
    }

    @Test
    fun `authenticated stream metadata timeout is typed`() = runTest {
        val metadata = mockk<YoutubeSessionStreamService>()
        coEvery { metadata.getStreamInfo(USER_ID, URL) } coAnswers {
            delay(60_000L)
            error("unreachable")
        }
        val service = YoutubeSessionSabrStreamService(
            metadata,
            mockk(relaxed = true),
            timeoutMs = 20L,
        )

        val result = withTimeout(1_000L) { service.getStreamInfo(USER_ID, URL) }

        assertTrue(result is ExtractionResult.Failure)
        assertEquals(AuthenticatedSabrPolicy.TIMEOUT_CODE, (result as ExtractionResult.Failure).code)
    }

    private fun service(
        visitor: () -> String = { SESSION_BINDING },
        token: (String, String, Boolean) -> SabrTokenBundle? = { _, _, _ -> sessionToken() },
        probe: (String, YoutubeSessionPoToken) -> YoutubeSabrInfo = { _, _ -> playableInfo() },
    ): AuthenticatedSabrInfoService {
        val sessions = mockk<YoutubeSessionService>()
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val probeMock = mockk<AuthenticatedSabrProbe>()
        coEvery { sessions.connectedCredentials(USER_ID) } returns credentials()
        coEvery { sessions.markUsed(USER_ID) } returns Unit
        every { tokenClient.fetchSession(any(), any(), any()) } answers {
            token(arg(0), arg(1), arg(2))
        }
        every { probeMock.fetch(any(), any()) } answers { probe(arg(0), arg(1)) }
        return AuthenticatedSabrInfoService(
            sessions,
            tokenClient,
            visitorDataFetcher = visitor,
            probe = probeMock,
            cache = AuthenticatedSabrInfoCache(timeoutMs = 1_200L),
        )
    }

    private fun blockingCall(): Nothing {
        Thread.sleep(5_000L)
        error("unreachable")
    }

    private fun playableInfo(): YoutubeSabrInfo = mockk {
        every { formats } returns listOf(
            mockk {
                every { isAudio } returns true
                every { isVideo } returns false
            },
            mockk {
                every { isAudio } returns false
                every { isVideo } returns true
            },
        )
    }

    private fun credentials() = YoutubeSessionCredentials(
        userId = USER_ID,
        fingerprint = "fingerprint",
        cookies = "SID=session-cookie",
        poToken = "session-player-token",
    )

    private fun sessionToken() = SabrTokenBundle(
        videoId = VIDEO_ID,
        visitorBoundPoToken = "public-session-token",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "public-visitor",
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
        sessionBinding = SESSION_BINDING,
        sessionBoundPoToken = "connected-session-token",
    )

    private companion object {
        const val USER_ID = "user-id"
        const val VIDEO_ID = "video-id"
        const val URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val SESSION_BINDING = "connected-visitor"
    }
}
