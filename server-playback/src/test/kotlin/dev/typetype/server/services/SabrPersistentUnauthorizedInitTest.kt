package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.localization.Localization
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class SabrPersistentUnauthorizedInitTest {
    @Test
    fun `a 403 that persists after the token refresh ends the session without new requests`() = runTest {
        val fixture = fixture()
        var refusals = 0
        val recovery = SabrUnauthorizedResponseRecovery({ refusals++ }) { token(byteArrayOf(2)) }

        val first = fetch(fixture, recovery)
        val second = fetch(fixture, recovery)

        assertNull(first)
        assertNull(second)
        assertEquals(SabrPlaybackState.TERMINAL, fixture.holder.playbackState())
        assertEquals(SabrFailureKind.UPSTREAM_REFUSED, fixture.holder.playbackStatus.terminalFailureKind())
        assertEquals(1, refusals)
        verify(exactly = 2) { fixture.session.fetchSegment(fixture.request, any()) }
    }

    @Test
    fun `a spent token refresh ends the session at the next 403`() = runTest {
        val fixture = fixture()
        fixture.holder.markUnauthorizedRefreshAttempted()
        val recovery = SabrUnauthorizedResponseRecovery { token(byteArrayOf(2)) }

        fetch(fixture, recovery)

        assertEquals(SabrFailureKind.UPSTREAM_REFUSED, fixture.holder.playbackStatus.terminalFailureKind())
        verify(exactly = 1) { fixture.session.fetchSegment(fixture.request, any()) }
    }

    @Test
    fun `a failed session answers init without asking YouTube again`() = runTest {
        val fixture = fixture()
        fixture.holder.failTerminal("refused", SabrFailureKind.UPSTREAM_REFUSED)
        val store = mockk<SabrSessionStore>()

        val result = SabrPlaybackSessionService(store).fetchInitialization(
            fixture.holder,
            fixture.holder.videoFormat,
            timeoutMs = 1_000L,
            generation = 0L,
        )

        assertInstanceOf(SabrPlaybackSegmentResult.Failed::class.java, result)
        coVerify(exactly = 0) { store.fetchInitializationData(any(), any()) }
    }

    private suspend fun fetch(fixture: Fixture, recovery: SabrUnauthorizedResponseRecovery): SabrMediaSegment? =
        fetchSabrInitializationSegment(
            fixture.holder,
            fixture.request,
            Localization("en", "US"),
            segmentCache = null,
            markServed = false,
            unauthorizedRecovery = recovery,
        )

    private fun fixture(): Fixture {
        val format = mockk<YoutubeSabrFormat>(relaxed = true)
        every { format.itag } returns 137
        val request = mockk<SabrSegmentRequest>()
        every { request.format } returns format
        val info = mockk<YoutubeSabrInfo>()
        every { info.videoId } returns VIDEO_ID
        every { info.visitorData } returns VISITOR_DATA
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { streamState.poToken } returns byteArrayOf(1)
        val requests = AtomicInteger(0)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.requestNumber } answers { requests.get() }
        every { session.diagnosticTrace } answers { "response n=${requests.get()} http=403 segments=count=0" }
        every { session.getCachedSegment(request) } returns null
        every { session.isBeyondEnd(request) } returns false
        every { session.fetchSegment(request, any()) } answers { requests.incrementAndGet(); null }
        val holder = SabrSessionHolder(
            session = session,
            info = info,
            audioFormat = format,
            videoFormat = format,
            sessionToken = "session",
            key = SabrSessionKey(VIDEO_ID, "user", 137, null, 137, 0L, SabrSessionPurpose.PLAYBACK),
            lastRequestAt = Instant.EPOCH,
            playerContextToken = token(byteArrayOf(1)),
        )
        return Fixture(holder, session, request)
    }

    private fun token(videoToken: ByteArray) = SabrTokenBundle(
        videoId = VIDEO_ID,
        visitorBoundPoToken = "visitor-token",
        visitorBoundPoTokenBytes = byteArrayOf(3),
        visitorData = VISITOR_DATA,
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = videoToken,
    )

    private data class Fixture(
        val holder: SabrSessionHolder,
        val session: YoutubeSabrSession,
        val request: SabrSegmentRequest,
    )

    private companion object {
        const val VIDEO_ID = "video-id"
        const val VISITOR_DATA = "visitor-data"
    }
}
