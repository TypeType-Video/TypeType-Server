package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrDemandWatchdogLifecycleTest {
    @Test
    fun `returning demand keeps its original deadline`() = runTest {
        withTracker { holder ->
            val first = SabrSegmentRequest.media(holder.audioFormat, 50)
            val second = SabrSegmentRequest.media(holder.audioFormat, 49)
            every { holder.session.isBeyondEnd(any()) } returns false
            every { holder.session.streamState.getSegmentStartMs(holder.audioFormat, 50) } returns 500_000L
            every { holder.session.streamState.getSegmentStartMs(holder.audioFormat, 49) } returns 400_000L
            holder.requestSegmentDemand(first, registeredAtMs = 0L)
            var expired = false
            val job = launch {
                expired = SabrDemandWatchdog(
                    clock = { testScheduler.currentTime },
                    intervalMs = 100L,
                ).monitor({ true }, holder)
            }
            runCurrent()

            advanceTimeBy(10_000L)
            holder.requestSegmentDemand(second, registeredAtMs = testScheduler.currentTime)
            runCurrent()
            advanceTimeBy(4_900L)
            holder.clearSegmentDemand(second)
            advanceTimeBy(100L)
            runCurrent()

            assertTrue(job.isCompleted)
            assertTrue(expired)
            assertEquals("SABR demand stalled for 140:50", holder.terminalFailure())
        }
    }

    @Test
    fun `discovered end removes demand without terminal failure`() = runTest {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.audioFormat, 85)
            var beyondEnd = false
            var alive = true
            every { holder.session.isBeyondEnd(request) } answers { beyondEnd }
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            var expired = true
            val job = launch {
                expired = SabrDemandWatchdog(
                    clock = { testScheduler.currentTime },
                    intervalMs = 100L,
                ).monitor({ alive }, holder)
            }
            runCurrent()

            advanceTimeBy(SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS - 100L)
            beyondEnd = true
            advanceTimeBy(100L)
            runCurrent()
            alive = false
            advanceTimeBy(100L)
            runCurrent()

            assertTrue(job.isCompleted)
            assertFalse(expired)
            assertFalse(holder.playbackState() == SabrPlaybackState.TERMINAL)
            assertEquals(null, holder.pendingSegmentDemandSummary())
        }
    }

    @Test
    fun `future live demand expires as recoverable`() = runTest {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.videoFormat, 50)
            every { holder.session.isLive } returns true
            every { holder.session.streamState.isLive } returns true
            every { holder.session.streamState.getMaxSegment(holder.videoFormat) } returns 49
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            var expired = false
            val job = launch {
                expired = SabrDemandWatchdog(
                    clock = { testScheduler.currentTime },
                    intervalMs = 100L,
                ).monitor({ true }, holder)
            }
            runCurrent()

            advanceTimeBy(SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS)
            runCurrent()
            advanceTimeBy(LIVE_EDGE_POLL_MS)
            runCurrent()

            assertTrue(job.isCompleted)
            assertTrue(expired)
            assertEquals(
                "$SABR_RECOVERABLE_FAILURE_PREFIX SABR demand stalled for 299:50",
                holder.terminalFailure(),
            )
        }
    }

    @Test
    fun `historical live demand outside recoverable window ignores backoff`() = runTest {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.videoFormat, 50)
            var liveHeadTimeMs = 470_250L
            every { holder.session.isLive } returns true
            every { holder.session.streamState.isLive } returns true
            every { holder.session.streamState.liveHeadTimeMs } answers { liveHeadTimeMs }
            every { holder.session.streamState.getSegmentStartMs(holder.videoFormat, 50) } returns 400_000L
            every { holder.session.streamState.getSegmentEndMs(holder.videoFormat, 50) } returns 410_000L
            every { holder.session.demandBackoffRemainingMs } returns 30_000L
            assertFalse(holder.isLiveDemandOutsideRecoverableWindow(request))
            liveHeadTimeMs++
            assertTrue(holder.isLiveDemandOutsideRecoverableWindow(request))
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            val identity = requireNotNull(holder.segmentDemandIdentity(request))
            assertTrue(holder.beginInFlightSegmentDemand(request, identity, futureLiveRequest = false))
            holder.clearSegmentDemand(request)
            var expired = false

            val job = launch {
                expired = SabrDemandWatchdog(
                    clock = { testScheduler.currentTime },
                    intervalMs = 100L,
                ).monitor({ true }, holder)
            }
            runCurrent()

            assertTrue(job.isCompleted)
            assertTrue(expired)
            assertEquals(
                "$SABR_RECOVERABLE_FAILURE_PREFIX SABR demand stalled for 299:50",
                holder.terminalFailure(),
            )
        }
    }

    @Test
    fun `completed in flight demand interrupts without terminal failure`() = runTest {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.videoFormat, 50)
            var cached = false
            var progressVersion = 0L
            every { holder.session.getCachedSegment(request) } answers {
                if (cached) mockk(relaxed = true) else null
            }
            every { holder.session.mediaProgressVersion } answers { progressVersion }
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            val identity = requireNotNull(holder.segmentDemandIdentity(request))
            assertTrue(holder.beginInFlightSegmentDemand(request, identity, futureLiveRequest = false))
            cached = true
            progressVersion = 1L
            holder.clearSegmentDemand(request)
            var interrupted = false
            val job = launch {
                interrupted = SabrDemandWatchdog(
                    clock = { testScheduler.currentTime },
                    intervalMs = 100L,
                ).monitor({ true }, holder)
            }
            runCurrent()

            advanceTimeBy(SabrPumpPolicy.COMPLETED_DEMAND_IDLE_MS)
            runCurrent()

            assertTrue(job.isCompleted)
            assertTrue(interrupted)
            assertFalse(holder.playbackState() == SabrPlaybackState.TERMINAL)
            assertEquals(null, holder.terminalFailure())
        }
    }

    @Test
    fun `missing in flight demand expires at its original deadline`() = runTest {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.videoFormat, 50)
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            val identity = requireNotNull(holder.segmentDemandIdentity(request))
            assertTrue(holder.beginInFlightSegmentDemand(request, identity, futureLiveRequest = false))
            holder.clearSegmentDemand(request)
            var expired = false
            val job = launch {
                expired = SabrDemandWatchdog(
                    clock = { testScheduler.currentTime },
                    intervalMs = 100L,
                ).monitor({ true }, holder)
            }
            runCurrent()

            advanceTimeBy(SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS)
            runCurrent()

            assertTrue(job.isCompleted)
            assertTrue(expired)
            assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
            assertEquals("SABR demand stalled for 299:50", holder.terminalFailure())
        }
    }

    private suspend fun withTracker(block: suspend (SabrSessionHolder) -> Unit) {
        SabrSegmentDemandTracker.clearAll()
        SabrInFlightDemandTracker.clearAll()
        try {
            block(holder())
        } finally {
            SabrSegmentDemandTracker.clearAll()
            SabrInFlightDemandTracker.clearAll()
        }
    }

    private fun holder(): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val audio = format(140, true)
        val video = format(299, false)
        every { session.getCachedSegment(any()) } returns null
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
