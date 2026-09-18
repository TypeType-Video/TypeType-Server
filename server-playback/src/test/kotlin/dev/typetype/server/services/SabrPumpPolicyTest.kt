package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrPumpLauncherTest {
    @Test
    fun `watchdog ignores companion progress for missing demand`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val pump = mockk<SabrSessionPump>()
            val registry = mockk<SabrSessionRegistry>()
            val holder = holder()
            val request = SabrSegmentRequest.media(holder.audioFormat, 50)
            var companionBufferedEndMs = 0L
            every { holder.session.getCachedSegment(any()) } returns null
            every { holder.session.streamState.getBufferedEndMs(holder.audioFormat) } returns 0L
            every { holder.session.streamState.getBufferedEndMs(holder.videoFormat) } answers { companionBufferedEndMs }
            every { registry.contains(holder) } returns true
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            coEvery { pump.pumpLoop(any(), holder, 100L) } coAnswers { awaitCancellation() }
            val watchdog = SabrDemandWatchdog(
                clock = { testScheduler.currentTime },
                intervalMs = 100L,
            )

            launchSabrPump(pump, registry, holder, 100L, watchdog)
            runCurrent()
            advanceTimeBy(SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS - 100L)
            runCurrent()
            companionBufferedEndMs = 10_000L
            assertFalse(holder.playbackState() == SabrPlaybackState.TERMINAL)
            advanceTimeBy(100L)
            runCurrent()
            advanceUntilIdle()

            assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
            assertEquals("SABR demand stalled for 140:50", holder.terminalFailure())
            assertNull(holder.pendingSegmentDemandSummary())
            holder.requestSegmentDemand(SabrSegmentRequest.media(holder.audioFormat, 51))
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            assertNull(holder.pendingSegmentDemandSummary())
            assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `watchdog ignores same format progress for missing demand`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val pump = mockk<SabrSessionPump>()
            val registry = mockk<SabrSessionRegistry>()
            val holder = holder()
            val request = SabrSegmentRequest.media(holder.audioFormat, 50)
            var targetBufferedEndMs = 0L
            every { holder.session.getCachedSegment(any()) } returns null
            every { holder.session.streamState.getBufferedEndMs(holder.audioFormat) } answers { targetBufferedEndMs }
            every { registry.contains(holder) } returns true
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            coEvery { pump.pumpLoop(any(), holder, 100L) } coAnswers { awaitCancellation() }
            val watchdog = SabrDemandWatchdog(
                clock = { testScheduler.currentTime },
                intervalMs = 100L,
            )

            launchSabrPump(pump, registry, holder, 100L, watchdog)
            runCurrent()
            advanceTimeBy(SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS - 100L)
            runCurrent()
            targetBufferedEndMs = 10_000L
            advanceTimeBy(100L)
            runCurrent()
            advanceUntilIdle()

            assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
            assertEquals("SABR demand stalled for 140:50", holder.terminalFailure())
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `watchdog gives a new exact demand its own deadline`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val pump = mockk<SabrSessionPump>()
            val registry = mockk<SabrSessionRegistry>()
            val holder = holder()
            val first = SabrSegmentRequest.media(holder.audioFormat, 50)
            val second = SabrSegmentRequest.media(holder.audioFormat, 51)
            every { holder.session.getCachedSegment(any()) } returns null
            every { registry.contains(holder) } returns true
            holder.requestSegmentDemand(first, registeredAtMs = 0L)
            coEvery { pump.pumpLoop(any(), holder, 100L) } coAnswers { awaitCancellation() }
            val watchdog = SabrDemandWatchdog(
                clock = { testScheduler.currentTime },
                intervalMs = 100L,
            )

            launchSabrPump(pump, registry, holder, 100L, watchdog)
            runCurrent()
            advanceTimeBy(SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS - 100L)
            runCurrent()
            holder.clearSegmentDemand(first)
            holder.requestSegmentDemand(second, registeredAtMs = testScheduler.currentTime)
            advanceTimeBy(100L)
            runCurrent()
            advanceTimeBy(SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS - 200L)
            runCurrent()
            assertFalse(holder.playbackState() == SabrPlaybackState.TERMINAL)
            advanceTimeBy(100L)
            runCurrent()
            advanceUntilIdle()

            assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
            assertEquals("SABR demand stalled for 140:51", holder.terminalFailure())
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `stale generation cannot register a segment demand`() {
        SabrSegmentDemandTracker.clearAll()
        try {
            val holder = holder()
            val request = SabrSegmentRequest.media(holder.audioFormat, 50)
            val staleGeneration = holder.activeGeneration()
            every { holder.session.getCachedSegment(any()) } returns null
            holder.advancePlaybackGeneration(1_000L)

            holder.requestSegmentDemand(request, staleGeneration)

            assertNull(holder.pendingSegmentDemandSummary())
            holder.requestSegmentDemand(request, holder.activeGeneration())
            assertEquals("140:50", holder.pendingSegmentDemandSummary())
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `terminal failure always has a message`() {
        val holder = holder()

        holder.failTerminal(null)

        assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
        assertEquals("SABR terminal failure", holder.terminalFailure())
    }

    @Test
    fun `first fatal playback cause wins`() {
        val networkHolder = holder()
        val terminalHolder = holder()

        networkHolder.recordNetworkFailure("network")
        networkHolder.failTerminal("terminal")
        terminalHolder.failTerminal("terminal")
        terminalHolder.recordNetworkFailure("network")

        assertEquals(SabrPlaybackState.NETWORK_FAILED, networkHolder.playbackState())
        assertEquals("network", networkHolder.networkFailure())
        assertNull(networkHolder.terminalFailure())
        assertEquals(SabrPlaybackState.TERMINAL, terminalHolder.playbackState())
        assertEquals("terminal", terminalHolder.terminalFailure())
        assertNull(terminalHolder.networkFailure())
    }

    @Test
    fun `failed network pump keeps a non-null failure and remains stopped`() = runTest {
        val pump = mockk<SabrSessionPump>()
        val registry = mockk<SabrSessionRegistry>()
        val holder = holder()
        holder.recordNetworkFailure(null)
        every { registry.contains(holder) } returns true
        coEvery { pump.pumpLoop(any(), holder, 100L) } returns Unit

        launchSabrPump(pump, registry, holder, 100L)
        advanceUntilIdle()

        assertEquals(SabrPlaybackState.NETWORK_FAILED, holder.playbackState())
        assertEquals("SABR network failure", holder.networkFailure())
        assertTrue(holder.markPumpStarted())
        holder.markPumpStopped()
        coVerify(exactly = 0) { pump.pumpLoop(any(), holder, 100L) }
    }

    private fun holder(): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val audio = format(140, true)
        val video = format(137, false)
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        return format
    }
}
