package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SabrDemandFailurePumpTest {
    @Test
    fun `later target response resolves demand without replacing session`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(audio, 39)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            val responseCount = AtomicInteger()
            val segment = mockk<SabrMediaSegment>(relaxed = true)
            every { session.streamState } returns streamState
            every { session.getCachedSegment(any()) } answers {
                segment.takeIf {
                    firstArg<SabrSegmentRequest>().format.itag == audio.itag &&
                        firstArg<SabrSegmentRequest>().sequenceNumber == request.sequenceNumber &&
                        responseCount.get() >= 4
                }
            }
            every { streamState.getSegmentStartMs(audio, 39) } returns 379_414L
            every { streamState.getMinBufferedEndMs() } returns 379_233L
            every { streamState.getBufferedEndMs(audio) } returns 379_233L
            every { session.pumpOnceStreamingForDemand(any(), request) } answers {
                responseCount.incrementAndGet()
                result()
            }
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)

            SabrSessionPump().pumpLoop(
                isAlive = { holder.pendingSegmentDemandSummary() != null },
                holder = holder,
                intervalMs = 100L,
            )

            assertFalse(holder.playbackState() == SabrPlaybackState.TERMINAL)
            assertNull(holder.terminalFailure())
            assertNull(holder.pendingSegmentDemandSummary())
            verify(exactly = 4) { session.pumpOnceStreamingForDemand(any(), request) }
            verify(exactly = 1) { session.prepareForMissingSegment(request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `cancelling pump interrupts a blocked demand call`() = runBlocking {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(audio, 50)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns streamState
            every { session.getCachedSegment(any()) } returns null
            every { streamState.getSegmentStartMs(audio, 50) } returns 499_414L
            every { streamState.getMinBufferedEndMs() } returns 499_233L
            val entered = CompletableDeferred<Unit>()
            val interrupted = AtomicBoolean(false)
            every { session.pumpOnceStreamingForDemand(any(), request) } answers {
                entered.complete(Unit)
                try {
                    Thread.sleep(60_000L)
                    emptyResult()
                } catch (error: InterruptedException) {
                    interrupted.set(true)
                    throw error
                }
            }
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            val job = launch(Dispatchers.IO) { SabrSessionPump().pumpLoop({ true }, holder, intervalMs = 0L) }

            entered.await()
            withTimeout(2_000L) { job.cancelAndJoin() }

            assertTrue(interrupted.get())
            verify(exactly = 1) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    private fun holder(
        session: YoutubeSabrSession,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ): SabrSessionHolder = SabrSessionHolder(
        session = session,
        info = mockk<YoutubeSabrInfo>(),
        audioFormat = audio,
        videoFormat = video,
        sessionToken = "session-token",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        return format
    }

    private fun result(): YoutubeSabrSession.DemandResponseResult {
        val result = mockk<YoutubeSabrSession.DemandResponseResult>()
        every { result.segmentCount } returns 7
        every { result.targetTrackSegmentCount } returns 1
        return result
    }

    private fun emptyResult(): YoutubeSabrSession.DemandResponseResult {
        val result = mockk<YoutubeSabrSession.DemandResponseResult>()
        every { result.segmentCount } returns 0
        every { result.targetTrackSegmentCount } returns 0
        return result
    }
}
