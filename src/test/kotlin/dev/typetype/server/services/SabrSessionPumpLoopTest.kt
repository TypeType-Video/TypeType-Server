package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrSessionPumpLoopTest {
    @Test
    fun `earliest audio and video demands remain queued`() {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, isAudio = true)
            val video = format(299, isAudio = false)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns streamState
            every { session.getCachedSegment(any()) } returns null
            every { streamState.getSegmentStartMs(audio, 42) } returns 420_000L
            every { streamState.getSegmentStartMs(video, 22) } returns 220_000L
            val holder = holder(session, audio, video)
            val audioRequest = SabrSegmentRequest.media(audio, 42)
            val videoRequest = SabrSegmentRequest.media(video, 22)

            holder.requestSegmentDemand(audioRequest)
            holder.requestSegmentDemand(videoRequest)

            assertEquals("299:22", holder.pendingSegmentDemandSummary())
            holder.clearSegmentDemand(videoRequest)
            assertEquals("140:42", holder.pendingSegmentDemandSummary())
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `recreated demand receives a fresh lifecycle identity`() {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, isAudio = true)
            val video = format(299, isAudio = false)
            val request = SabrSegmentRequest.media(video, 22)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            every { session.streamState } returns mockk(relaxed = true)
            every { session.getCachedSegment(any()) } returns null
            val holder = holder(session, audio, video)

            holder.requestSegmentDemand(request)
            val firstIdentity = holder.segmentDemandIdentity(request)
            holder.clearSegmentDemand(request)
            holder.requestSegmentDemand(request)
            val recreatedIdentity = holder.segmentDemandIdentity(request)
            holder.advancePlaybackGeneration(220_000L)
            holder.requestSegmentDemand(request)
            val nextGenerationIdentity = holder.segmentDemandIdentity(request)

            assertNotEquals(firstIdentity, recreatedIdentity)
            assertNotEquals(recreatedIdentity, nextGenerationIdentity)
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `fresh pump waits for a reader demand`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.requestNumber } returns 0
        every { session.streamState } returns mockk(relaxed = true)
        val holder = holder(session, audio, video)
        var rounds = 0

        SabrSessionPump().pumpLoop({ rounds++ == 0 }, holder, intervalMs = 100L)

        verify(exactly = 0) { session.pumpOnceStreaming(any()) }
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test
    fun `active live pump maintains read ahead after warmup`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.requestNumber } returns 42
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { streamState.isPostLiveDvr } returns false
        every { streamState.isLive } returns true
        val holder = holder(session, audio, video)
        holder.setReaderPosition(audio, 1_000_000L)
        holder.setReaderPosition(video, 1_000_000L)
        var rounds = 0

        SabrSessionPump().pumpLoop({ rounds++ == 0 }, holder, intervalMs = 100L)

        verify(exactly = 1) { session.pumpOnceStreaming(any()) }
        verify(exactly = 1) { session.setPlayHeadMs(match { it in 988_000L..998_000L }) }
        verify(exactly = 1) { session.evictPlayed() }
        assertEquals(LIVE_EDGE_POLL_MS, testScheduler.currentTime)
    }

    @Test
    fun `non target media response keeps demand loop paced`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, isAudio = true)
            val video = format(299, isAudio = false)
            val request = SabrSegmentRequest.media(video, 98)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            every { session.streamState } returns mockk(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>()
            every { session.streamState } returns streamState
            every { streamState.setActiveTrackTypes(true, true) } returns Unit
            every { session.setPlayHeadMs(any()) } returns Unit
            every { session.evictPlayed() } returns Unit
            every { session.requestNumber } returns 12
            every { session.cachedBytes } returns 6_607_100L
            every { session.diagnosticTrace } returns ""
            val rebased = mediaSegment(sequence = 101, startMs = 488_200L, durationMs = 6_500L)
            every { session.getCachedSegment(any()) } answers {
                firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 101 }?.let { rebased }
            }
            every { streamState.getMinBufferedEndMs() } returns 487_134L
            every { streamState.getBufferedEndMs(video) } returns 491_203L
            every { streamState.getSegmentStartMs(video, 98) } returns 491_203L
            every { streamState.setPlayerTimeMs(any()) } returns Unit
            every { streamState.jumpBufferedTo(video, 101) } returns Unit
            every { session.pumpOnceStreamingForDemand(any(), request) } returns demandResult(5, 0)
            val holder = holder(session, audio, video)
            holder.setPlayerTimeMs(491_203L)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ == 0 }, holder, intervalMs = 100L)

            assertEquals(0L, testScheduler.currentTime)
            assertEquals(null, holder.pendingSegmentDemandSummary())
            verify(exactly = 1) { streamState.jumpBufferedTo(video, 101) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `segment demand pumps requested segment without forward jump`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, isAudio = true)
            val video = format(247, isAudio = false)
            val request = SabrSegmentRequest.media(audio, 35)
            val session = mockk<YoutubeSabrSession> { every { isBeyondEnd(request) } returns false }
            val streamState = mockk<YoutubeSabrStreamState>()
            every { session.streamState } returns streamState
            every { streamState.setActiveTrackTypes(true, true) } returns Unit
            every { session.setPlayHeadMs(any()) } returns Unit
            every { session.evictPlayed() } returns Unit
            every { session.requestNumber } returns 9
            every { session.cachedBytes } returns 0L
            every { session.diagnosticTrace } returns ""
            every { session.getCachedSegment(any()) } returns null
            every { streamState.getMinBufferedEndMs() } returns 329_492L
            every { streamState.getBufferedEndMs(audio) } returns 349_461L
            every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 340_000L) } returns 64
            every { streamState.getSegmentStartMs(audio, 35) } returns 340_000L
            every { streamState.getSegmentEndMs(audio, 34) } returns 339_476L
            every { streamState.getSegmentEndMs(audio, 35) } returns 349_461L
            every { streamState.getSegmentEndMs(video, 63) } returns 333_800L
            every { streamState.setPlayerTimeMs(any()) } returns Unit
            every { streamState.setActiveTrackTypes(false, true) } returns Unit
            every { streamState.setBufferedRangesOverride(any()) } returns Unit
            every { streamState.setBufferedRangesOverride(null) } returns Unit
            every { streamState.setSelectVideoFormatBeforeAudio(any()) } returns Unit
            every { session.pumpOnceStreamingForDemand(any(), request) } returns demandResult(0, 0)
            val holder = holder(session, audio, video)
            holder.setPlayerTimeMs(340_000L)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ == 0 }, holder, intervalMs = 0L)

            verify(exactly = 0) { session.prepareForForwardJump(request) }
            verify(exactly = 1) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `near edge demand keeps PipePipe server ahead margin`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, isAudio = true)
            val video = format(299, isAudio = false)
            val request = SabrSegmentRequest.media(video, 28)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns streamState
            every { session.getCachedSegment(any()) } returns null
            every { session.requestNumber } returns 7
            every { streamState.getMinBufferedEndMs() } returns 121_440L
            every { streamState.getSegmentStartMs(video, 28) } returns 136_620L
            every { session.pumpOnceStreamingForDemand(any(), request) } returns demandResult(0, 0)
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ == 0 }, holder, intervalMs = 0L)

            verify(exactly = 1) { streamState.setPlayerTimeMs(105_440L) }
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
        every { format.lastModified } returns if (isAudio) AUDIO_LAST_MODIFIED else VIDEO_LAST_MODIFIED
        every { format.xtags } returns null
        return format
    }

    private fun mediaSegment(sequence: Int, startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        every { header.itag } returns 299
        every { header.isInitSegment } returns false
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }

    private fun demandResult(
        segmentCount: Int,
        targetTrackSegmentCount: Int,
    ): YoutubeSabrSession.DemandResponseResult {
        val result = mockk<YoutubeSabrSession.DemandResponseResult>()
        every { result.segmentCount } returns segmentCount
        every { result.targetTrackSegmentCount } returns targetTrackSegmentCount
        return result
    }

    private companion object {
        const val AUDIO_LAST_MODIFIED = 1_765_814_035_331_078L
        const val VIDEO_LAST_MODIFIED = 1_726_365_891_623_401L
    }
}
