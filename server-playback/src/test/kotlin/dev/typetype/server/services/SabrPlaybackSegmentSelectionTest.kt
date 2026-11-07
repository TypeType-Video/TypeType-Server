package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class SabrPlaybackSegmentSelectionTest {
    private val audio = format(251, isAudio = true)
    private val video = format(271, isAudio = false)
    private val state = mockk<YoutubeSabrStreamState>(relaxed = true)
    private val holder = holder()

    @Test
    fun `an indexed video takes segment times from its index after a far seek`() {
        every { state.hasSegmentIndex(video) } returns true
        every { state.getSegmentStartMs(video, 28) } returns 145_625L
        every { state.getSegmentEndMs(video, 28) } returns 151_000L
        holder.observeMediaSegment(mediaSegment(video.itag, sequence = 63, startMs = 335_625L, durationMs = 6_250L))

        assertEquals(145_625L, holder.playbackSegmentStartMs(video, 28))
        assertEquals(5_375L, holder.playbackSegmentDurationMs(video, 28))
        assertEquals(151_000L, holder.playbackSegmentEndMs(video, 28))
    }

    @Test
    fun `a stream without an index still extrapolates from the observed segment`() {
        every { state.hasSegmentIndex(video) } returns false
        holder.observeMediaSegment(mediaSegment(video.itag, sequence = 200, startMs = 1_000_000L, durationMs = 5_000L))

        assertEquals(1_010_000L, holder.playbackSegmentStartMs(video, 202))
        assertEquals(5_000L, holder.playbackSegmentDurationMs(video, 202))
    }

    private fun holder(): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        every { session.streamState } returns state
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
        every { format.audioTrackId } returns null
        return format
    }

    private fun mediaSegment(itag: Int, sequence: Int, startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>(relaxed = true)
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        every { header.isInitSegment } returns false
        return mockk { every { this@mockk.header } returns header }
    }
}
