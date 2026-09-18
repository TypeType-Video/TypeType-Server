package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo

class AuthenticatedSabrInfoServiceTest {
    @Test
    fun `connected account uses one authenticated token pair`() = runBlocking {
        val sessions = mockk<YoutubeSessionService>()
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val probe = mockk<AuthenticatedSabrProbe>()
        val token = sessionToken()
        val info = playableInfo()
        coEvery { sessions.connectedCredentials(USER_ID) } returns credentials(USER_ID)
        coEvery { sessions.markUsed(USER_ID) } returns Unit
        every { tokenClient.fetchSession(VIDEO_ID, SESSION_BINDING, false) } returns token
        every { probe.fetch(VIDEO_ID, any()) } answers {
            val supplied = secondArg<YoutubeSessionPoToken>()
            assertEquals(SESSION_BINDING, supplied.visitorData)
            assertEquals(SESSION_PO_TOKEN, supplied.poToken)
            info
        }
        val service = AuthenticatedSabrInfoService(
            sessions,
            tokenClient,
            visitorDataFetcher = { SESSION_BINDING },
            probe = probe,
        )

        val result = service.fetch(USER_ID, VIDEO_ID) as AuthenticatedSabrInfoResult.Ready

        assertSame(info, result.prepared.info)
        assertSame(token, result.prepared.initialToken)
        assertEquals(SabrPreparedSource.AUTHENTICATED_YOUTUBE, result.prepared.source)
        coVerify(exactly = 1) { sessions.markUsed(USER_ID) }
    }

    @Test
    fun `reuses authenticated info for the following playback request`() = runBlocking {
        val sessions = mockk<YoutubeSessionService>()
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val probe = mockk<AuthenticatedSabrProbe>()
        coEvery { sessions.connectedCredentials(USER_ID) } returns credentials(USER_ID)
        coEvery { sessions.markUsed(USER_ID) } returns Unit
        every { tokenClient.fetchSession(VIDEO_ID, SESSION_BINDING, false) } returns sessionToken()
        every { probe.fetch(VIDEO_ID, any()) } returns playableInfo()
        val service = AuthenticatedSabrInfoService(
            sessions,
            tokenClient,
            visitorDataFetcher = { SESSION_BINDING },
            probe = probe,
        )

        val metadata = service.fetch(USER_ID, VIDEO_ID)
        val playback = service.fetch(USER_ID, VIDEO_ID)

        assertSame((metadata as AuthenticatedSabrInfoResult.Ready).prepared, (playback as AuthenticatedSabrInfoResult.Ready).prepared)
        verify(exactly = 1) { tokenClient.fetchSession(VIDEO_ID, SESSION_BINDING, false) }
        verify(exactly = 1) { probe.fetch(VIDEO_ID, any()) }
        coVerify(exactly = 1) { sessions.markUsed(USER_ID) }
    }

    @Test
    fun `guest playback does not inspect connected credentials`() = runBlocking {
        val sessions = mockk<YoutubeSessionService>()
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val service = AuthenticatedSabrInfoService(sessions, tokenClient)

        val result = service.fetch("guest:anonymous", VIDEO_ID)

        assertEquals(AuthenticatedSabrInfoResult.NotConnected, result)
        coVerify(exactly = 0) { sessions.connectedCredentials(any()) }
        verify(exactly = 0) { tokenClient.fetchSession(any(), any(), any()) }
    }

    @Test
    fun `authenticated probe failure is typed and does not mark session used`() = runBlocking {
        val sessions = mockk<YoutubeSessionService>()
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        coEvery { sessions.connectedCredentials(USER_ID) } returns credentials(USER_ID)
        every { tokenClient.fetchSession(VIDEO_ID, SESSION_BINDING, false) } returns null
        val service = AuthenticatedSabrInfoService(
            sessions,
            tokenClient,
            visitorDataFetcher = { SESSION_BINDING },
        )

        val result = service.fetch(USER_ID, VIDEO_ID)

        assertEquals(AuthenticatedSabrInfoResult.Failed, result)
        coVerify(exactly = 0) { sessions.markUsed(any()) }
    }

    @Test
    fun `cancellation remains observable`() {
        val sessions = mockk<YoutubeSessionService>()
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val probe = mockk<AuthenticatedSabrProbe>()
        coEvery { sessions.connectedCredentials(USER_ID) } returns credentials(USER_ID)
        every { tokenClient.fetchSession(VIDEO_ID, SESSION_BINDING, false) } returns sessionToken()
        every { probe.fetch(VIDEO_ID, any()) } throws CancellationException("cancelled")
        val service = AuthenticatedSabrInfoService(
            sessions,
            tokenClient,
            visitorDataFetcher = { SESSION_BINDING },
            probe = probe,
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { service.fetch(USER_ID, VIDEO_ID) }
        }
    }

    private fun playableInfo(): YoutubeSabrInfo = mockk {
        every { formats } returns listOf(
            mockk<YoutubeSabrFormat> {
                every { isAudio } returns true
                every { isVideo } returns false
            },
            mockk<YoutubeSabrFormat> {
                every { isAudio } returns false
                every { isVideo } returns true
            },
        )
    }

    private fun credentials(userId: String) = YoutubeSessionCredentials(
        userId = userId,
        fingerprint = "fingerprint-$userId",
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
        sessionBoundPoToken = SESSION_PO_TOKEN,
    )

    private companion object {
        const val USER_ID = "user-id"
        const val VIDEO_ID = "video-id"
        const val SESSION_BINDING = "connected-visitor"
        const val SESSION_PO_TOKEN = "connected-session-token"
    }
}
