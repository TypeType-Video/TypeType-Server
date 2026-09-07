package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSeekRepositionPumpTest {
    @Test
    fun `missing audio anchors rewind when edge is between paired starts`() {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val videoRequest = SabrSegmentRequest.media(video, 24)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val state = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns state
            every { session.getCachedSegment(any()) } returns null
            every { state.getSegmentNumberAtOrAfterTimeMs(video, 120_000L) } returns 24
            every { state.getSegmentNumberAtOrAfterTimeMs(audio, 120_000L) } returns 13
            every { state.getSegmentStartMs(video, 24) } returns 119_604L
            every { state.getSegmentStartMs(audio, 13) } returns 118_979L
            every { state.getMinBufferedEndMs() } returns 119_200L
            every {
                session.getCachedSegment(match { it.format.itag == videoRequest.format.itag && it.sequenceNumber == 24 })
            } returns mockk<SabrMediaSegment>()
            val holder = holder(session, audio, video)
            val store = mockk<SabrSessionStore>(relaxed = true)

            SabrPlaybackSessionService(store).seekExisting(holder, 120_000L)

            assertEquals("140:13", holder.pendingSegmentDemandSummary())
            val seek = holder.consumeRefetch()
            assertEquals(140, seek?.format?.itag)
            assertEquals(13, seek?.sequenceNumber)
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `forward seek uses target demand pump without normal backoff`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(video, 24)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            every { session.streamState } returns mockk(relaxed = true)
            every { session.requestNumber } returns 2
            every { session.getCachedSegment(any()) } returns null
            every { session.pumpOnceStreamingForDemand(any(), request) } returns mockk(relaxed = true)
            val holder = holder(session, audio, video)
            holder.setRequestedSeekTimeMs(120_321L)
            holder.requestSegmentDemand(request)
            holder.requestForwardSeek(request)
            var rounds = 0

            SabrSessionPumpLoop().run({ rounds++ < 1 }, holder, intervalMs = 0L)

            verify(exactly = 1) { session.prepareForForwardJump(request, 120_321L) }
            verify(exactly = 0) { session.prepareForForwardJump(request) }
            verify(exactly = 0) { session.pumpOnceStreaming(any()) }
            verify(exactly = 1) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `cold playback bootstraps before applying saved position`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(video, 180)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val state = mockk<YoutubeSabrStreamState>(relaxed = true)
            var requestNumber = 0
            every { session.streamState } returns state
            every { session.requestNumber } answers { requestNumber }
            every { session.getCachedSegment(any()) } returns null
            every { session.pumpOnceStreaming(any()) } answers {
                requestNumber = 1
                2
            }
            every { state.getMaxSegment(audio) } returns 1
            every { state.getMaxSegment(video) } returns 1
            every { session.pumpOnceStreamingForDemand(any(), request) } returns mockk(relaxed = true)
            val holder = holder(session, audio, video)
            holder.setRequestedSeekTimeMs(900_000L)
            holder.requestSegmentDemand(request)
            holder.requestForwardSeek(request)
            var rounds = 0

            SabrSessionPumpLoop().run({ rounds++ < 2 }, holder, intervalMs = 0L)

            verifyOrder {
                state.setPlayerTimeMs(0L)
                session.pumpOnceStreaming(any())
                session.prepareForForwardJump(request, 900_000L)
                session.pumpOnceStreamingForDemand(any(), request)
            }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `forward seek discovered beyond end is discarded`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(video, 24)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            var beyondEnd = false
            every { session.streamState } returns mockk(relaxed = true)
            every { session.getCachedSegment(any()) } returns null
            every { session.isBeyondEnd(request) } answers { beyondEnd }
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            holder.requestForwardSeek(request)
            beyondEnd = true
            var rounds = 0

            SabrSessionPumpLoop().run({ rounds++ < 1 }, holder, intervalMs = 0L)

            verify(exactly = 0) { session.prepareForForwardJump(request) }
            verify(exactly = 0) { session.pumpOnceStreamingForDemand(any(), request) }
            assertEquals(null, holder.pendingSegmentDemandSummary())
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `historical live demand rewinds from observed media edge`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(299, false)
            val request = SabrSegmentRequest.media(audio, 3_073)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val state = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns state
            every { session.isLive } returns true
            every { state.isLive } returns true
            every { state.isPostLiveDvr } returns false
            every { session.getCachedSegment(any()) } returns null
            every { session.pumpOnceStreamingForDemand(any(), request) } returns mockk(relaxed = true)
            val holder = holder(session, audio, video)
            holder.observeMediaSegment(mediaSegment(audio.itag, sequence = 3_076))
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPumpLoop().run({ rounds++ < 1 }, holder, intervalMs = 0L)

            verify(exactly = 1) { session.prepareForRewind(request) }
            verify(exactly = 0) { session.prepareForMediaSegment(request) }
            verify(exactly = 1) { state.setBufferedRangesOverride(null) }
            verify(exactly = 1) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `historical live demand keeps rewinding after the observed segment`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(299, false)
            val request = SabrSegmentRequest.media(video, 3_077)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val state = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns state
            every { session.isLive } returns true
            every { state.isLive } returns true
            every { state.isPostLiveDvr } returns false
            every { state.liveHeadTimeMs } returns 15_750_000L
            every { session.getCachedSegment(any()) } returns null
            every { session.pumpOnceStreamingForDemand(any(), request) } returns mockk(relaxed = true)
            val holder = holder(session, audio, video)
            holder.observeMediaSegment(mediaSegment(video.itag, sequence = 3_076))
            holder.setLastServedSequence(video.itag, 3_076)
            holder.requestSegmentDemand(request)
            assertFalse(holder.isFutureLiveRequest(request))
            assertEquals(DEFAULT_PLAYBACK_RETRY_MS, holder.liveRetryAfterMs(listOf(request)))
            var rounds = 0

            SabrSessionPumpLoop().run({ rounds++ < 1 }, holder, intervalMs = 0L)

            verify(exactly = 1) { session.prepareForRewind(request) }
            verify(exactly = 1) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `historical live seek preserves its exact player position`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(299, false)
            val request = SabrSegmentRequest.media(audio, 3_073)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val state = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns state
            every { session.isLive } returns true
            every { state.isLive } returns true
            every { state.isPostLiveDvr } returns false
            every { session.getCachedSegment(any()) } returns null
            every { session.pumpOnceStreamingForDemand(any(), request) } returns mockk(relaxed = true)
            val holder = holder(session, audio, video)
            holder.observeMediaSegment(mediaSegment(audio.itag, sequence = 3_076))
            holder.setRequestedSeekTimeMs(15_365_500L)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPumpLoop().run({ rounds++ < 1 }, holder, intervalMs = 0L)

            verify(exactly = 1) { session.prepareForRewind(request, 15_365_500L) }
            verify(exactly = 0) { session.prepareForRewind(request) }
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
        every { format.audioTrackId } returns null
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        every { format.lastModified } returns 0L
        every { format.xtags } returns ""
        return format
    }

    private fun mediaSegment(itag: Int, sequence: Int): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>(relaxed = true)
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns sequence * 5_000L
        every { header.durationMs } returns 5_000L
        every { header.isInitSegment } returns false
        return mockk { every { this@mockk.header } returns header }
    }
}
