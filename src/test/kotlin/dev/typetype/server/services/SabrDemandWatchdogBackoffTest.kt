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
class SabrDemandWatchdogBackoffTest {
    @Test
    fun `queued demand deadline excludes thirty second backoff`() = runTest {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.audioFormat, 50)
            val backoffUntilMs = 30_000L
            every { holder.session.demandBackoffRemainingMs } answers {
                (backoffUntilMs - testScheduler.currentTime).coerceAtLeast(0L)
            }
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            var expired = false
            val job = launch {
                expired = watchdog { testScheduler.currentTime }.monitor({ true }, holder)
            }
            runCurrent()

            advanceTimeBy(44_900L)
            runCurrent()
            assertFalse(job.isCompleted)

            advanceTimeBy(100L)
            runCurrent()
            assertTrue(job.isCompleted)
            assertTrue(expired)
            assertEquals("SABR demand stalled for 140:50", holder.terminalFailure())
        }
    }

    @Test
    fun `in flight deadline excludes twenty second backoff`() = runTest {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.videoFormat, 50)
            val backoffUntilMs = 20_000L
            every { holder.session.demandBackoffRemainingMs } answers {
                (backoffUntilMs - testScheduler.currentTime).coerceAtLeast(0L)
            }
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            val identity = requireNotNull(holder.segmentDemandIdentity(request))
            assertTrue(holder.beginInFlightSegmentDemand(request, identity, futureLiveRequest = false))
            holder.clearSegmentDemand(request)
            var expired = false
            val job = launch {
                expired = watchdog { testScheduler.currentTime }.monitor({ true }, holder)
            }
            runCurrent()

            advanceTimeBy(34_900L)
            runCurrent()
            assertFalse(job.isCompleted)

            advanceTimeBy(100L)
            runCurrent()
            assertTrue(job.isCompleted)
            assertTrue(expired)
            assertEquals("SABR demand stalled for 299:50", holder.terminalFailure())
        }
    }

    @Test
    fun `renewed backoffs cannot extend a demand forever`() = runTest {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.videoFormat, 50)
            every { holder.session.demandBackoffRemainingMs } returns 2_000L
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            var expired = false
            val job = launch {
                expired = watchdog { testScheduler.currentTime }.monitor({ true }, holder)
            }
            runCurrent()

            advanceTimeBy(44_900L)
            runCurrent()
            assertFalse(job.isCompleted)

            advanceTimeBy(100L)
            runCurrent()
            assertTrue(job.isCompleted)
            assertTrue(expired)
            assertEquals("SABR demand stalled for 299:50", holder.terminalFailure())
        }
    }

    private fun watchdog(clock: () -> Long): SabrDemandWatchdog = SabrDemandWatchdog(
        clock = clock,
        intervalMs = 100L,
    )

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
        every { session.mediaProgressVersion } returns 0L
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "backoff-session",
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
