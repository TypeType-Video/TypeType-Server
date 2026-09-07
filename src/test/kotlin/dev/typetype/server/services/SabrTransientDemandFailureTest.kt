package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrTransientDemandFailureTest {
    @Test
    fun `companion responses keep demand pending until target arrives`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(audio, 50)
            val segment = mediaSegment(140, 50, 499_414L)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            var cached = false
            var attempts = 0
            every { session.streamState } returns streamState
            every { session.requestNumber } answers { attempts }
            every { session.getCachedSegment(request) } answers { segment.takeIf { cached } }
            every { streamState.getSegmentStartMs(audio, 50) } returns 499_414L
            every { streamState.getMinBufferedEndMs() } returns 499_233L
            every { session.pumpOnceStreamingForDemand(any(), request) } answers {
                attempts++
                if (attempts == 3) cached = true
                result(segmentCount = if (attempts < 3) 4 else 1, targetTrackSegmentCount = if (attempts < 3) 0 else 1)
            }
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ < 3 }, holder, intervalMs = 0L)

            assertEquals(SabrPlaybackState.STOPPED, holder.playbackState())
            assertNull(holder.terminalFailure())
            assertNull(holder.pendingSegmentDemandSummary())
            verify(exactly = 0) { session.prepareForRewind(request) }
            verify(exactly = 3) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `transient timeout preserves demand until retry succeeds`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(audio, 39)
            val segment = mediaSegment(140, 39, 379_414L)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            val result = mockk<YoutubeSabrSession.DemandResponseResult>()
            var cached = false
            var attempts = 0
            every { session.streamState } returns streamState
            every { session.getCachedSegment(request) } answers { segment.takeIf { cached } }
            every { streamState.getSegmentStartMs(audio, 39) } returns 379_414L
            every { streamState.getMinBufferedEndMs() } returns 379_233L
            every { result.segmentCount } returns 1
            every { result.targetTrackSegmentCount } returns 1
            every { session.pumpOnceStreamingForDemand(any(), request) } answers {
                attempts++
                if (attempts == 1) throw IOException("timeout")
                cached = true
                result
            }
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ < 2 }, holder, intervalMs = 0L)

            assertEquals(SabrPlaybackState.STOPPED, holder.playbackState())
            assertNull(holder.terminalFailure())
            assertNull(holder.pendingSegmentDemandSummary())
            assertEquals(1_000L, testScheduler.currentTime)
            verify(exactly = 2) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `no response demand rounds preserve network failure streak`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(audio, 50)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            var attempts = 0
            every { session.streamState } returns streamState
            every { session.requestNumber } returns 3
            every { session.getCachedSegment(request) } returns null
            every { streamState.getSegmentStartMs(audio, 50) } returns 499_414L
            every { streamState.getMinBufferedEndMs() } returns 499_233L
            every { session.pumpOnceStreamingForDemand(any(), request) } answers {
                attempts++
                if (attempts % 2 == 1) throw IOException("timeout")
                result(segmentCount = 0, targetTrackSegmentCount = 0)
            }
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ < 9 }, holder, intervalMs = 0L)

            assertEquals(SabrPlaybackState.NETWORK_FAILED, holder.playbackState())
            assertEquals("timeout", holder.networkFailure())
            verify(exactly = 9) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `successful response resets network failure streak`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(audio, 50)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            var attempts = 0
            var requestNumber = 3
            every { session.streamState } returns streamState
            every { session.requestNumber } answers { requestNumber }
            every { session.getCachedSegment(request) } returns null
            every { streamState.getSegmentStartMs(audio, 50) } returns 499_414L
            every { streamState.getMinBufferedEndMs() } returns 499_233L
            every { session.pumpOnceStreamingForDemand(any(), request) } answers {
                attempts++
                if (attempts != 5) throw IOException("timeout")
                requestNumber++
                result(segmentCount = 4, targetTrackSegmentCount = 0)
            }
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ < 9 }, holder, intervalMs = 0L)

            assertEquals(SabrPlaybackState.STOPPED, holder.playbackState())
            assertNull(holder.networkFailure())
            verify(exactly = 9) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `replaced demand ignores an in flight response from the old generation`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(audio, 50)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            lateinit var holder: SabrSessionHolder
            var attempts = 0
            every { session.streamState } returns streamState
            every { session.requestNumber } answers { attempts }
            every { session.getCachedSegment(request) } returns null
            every { streamState.getSegmentStartMs(audio, 50) } returns 499_414L
            every { streamState.getMinBufferedEndMs() } returns 499_233L
            every { session.pumpOnceStreamingForDemand(any(), request) } answers {
                attempts++
                if (attempts == 3) {
                    synchronized(holder) {
                        holder.clearSegmentDemands()
                        holder.advancePlaybackGeneration(499_414L)
                        holder.requestSegmentDemand(request)
                    }
                }
                result(segmentCount = 4, targetTrackSegmentCount = 1)
            }
            holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ < 3 }, holder, intervalMs = 0L)

            assertEquals(SabrPlaybackState.STOPPED, holder.playbackState())
            assertNull(holder.terminalFailure())
            assertEquals("140:50", holder.pendingSegmentDemandSummary())
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

    private fun result(segmentCount: Int, targetTrackSegmentCount: Int): YoutubeSabrSession.DemandResponseResult {
        val result = mockk<YoutubeSabrSession.DemandResponseResult>()
        every { result.segmentCount } returns segmentCount
        every { result.targetTrackSegmentCount } returns targetTrackSegmentCount
        return result
    }

    private fun mediaSegment(itag: Int, sequence: Int, startMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.isInitSegment } returns false
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }
}
