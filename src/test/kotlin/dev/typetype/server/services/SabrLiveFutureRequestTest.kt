package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrLiveFutureRequestTest {
    @Test
    fun `segment behind the reported live head is not treated as future`() {
        val audio = format(140)
        val video = format(299)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { session.liveHeadSequenceNumber } returns 230L
        every { session.getCachedSegment(any()) } returns null
        every { state.isLive } returns true
        every { state.liveHeadSequenceNumber } returns 230L
        every { state.liveHeadTimeMs } returns 1_060_000L
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )

        assertFalse(holder.isFutureLiveRequest(SabrSegmentRequest.media(video, 201)))
    }

    @Test
    fun `audio segment behind the live time is not treated as future`() {
        val audio = format(140)
        val video = format(299)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { session.liveHeadSequenceNumber } returns 3_920L
        every { session.getCachedSegment(any()) } returns null
        every { state.isLive } returns true
        every { state.liveHeadSequenceNumber } returns 3_920L
        every { state.liveHeadTimeMs } returns 7_842_000L
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
        holder.observeMediaSegment(segment(audio.itag, 3_889, 7_776_000L))
        holder.setLastServedSequence(audio.itag, 3_889)
        val request = SabrSegmentRequest.media(audio, 3_890)

        assertFalse(holder.isFutureLiveRequest(request))
        assertTrue(holder.isHistoricalLiveRequest(request))
    }

    private fun format(itag: Int): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
    }

    private fun segment(itag: Int, sequence: Int, startMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader> {
            every { isInitSegment } returns false
            every { this@mockk.itag } returns itag
            every { sequenceNumber } returns sequence
            every { this@mockk.startMs } returns startMs
            every { durationMs } returns 2_000L
        }
        return mockk { every { this@mockk.header } returns header }
    }
}
