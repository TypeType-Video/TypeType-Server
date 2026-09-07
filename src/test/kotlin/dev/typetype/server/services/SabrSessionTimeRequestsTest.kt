package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSessionTimeRequestsTest {
    @Test
    fun `mediaRequestsAt returns active audio and video requests for player time`() {
        val audio = sabrFormat(itag = 140, isAudio = true)
        val video = sabrFormat(itag = 137, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.getSegmentNumberAtOrAfterTimeMs(video, 321_601L) } returns 64
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 321_601L) } returns 33
        val holder = holder(session, audio, video)

        val requests = holder.mediaRequestsAt(321_601L)

        assertEquals(listOf(137, 140), requests.map { it.format.itag })
        assertEquals(listOf(64, 33), requests.map { it.sequenceNumber })
    }

    @Test
    fun `mediaRequestsAt uses mapped video and audio sequences`() {
        val audio = sabrFormat(itag = 140, isAudio = true)
        val video = sabrFormat(itag = 247, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.getSegmentNumberAtOrAfterTimeMs(video, 340_000L) } returns 64
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 340_000L) } returns 35
        val holder = holder(session, audio, video)

        val requests = holder.mediaRequestsAt(340_000L)

        assertEquals(listOf(247, 140), requests.map { it.format.itag })
        assertEquals(listOf(64, 35), requests.map { it.sequenceNumber })
    }

    @Test
    fun `mediaRequestsAt excludes inactive tracks`() {
        val audio = sabrFormat(itag = 140, isAudio = true)
        val video = sabrFormat(itag = 137, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.getSegmentNumberAtOrAfterTimeMs(video, 321_601L) } returns 64
        val holder = holder(session, audio, video)
        holder.setActiveTracks(videoActive = true, audioActive = false)

        val requests = holder.mediaRequestsAt(321_601L)

        assertEquals(listOf(137), requests.map { it.format.itag })
        assertEquals(listOf(64), requests.map { it.sequenceNumber })
    }

    @Test
    fun `reposition keeps a non adjacent live boundary request pending`() {
        val audio = sabrFormat(itag = 140, isAudio = true)
        val video = sabrFormat(itag = 248, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.getSegmentStartMs(audio, 100) } returns 995_000L
        every { session.getCachedSegment(any()) } returns null
        val holder = holder(session, audio, video)
        val observed = mediaSegment(audio.itag, sequence = 102, startMs = 995_010L)
        holder.markExpectedLive()
        holder.observeMediaSegment(observed)
        every {
            session.getCachedSegment(match {
                it.format.itag == audio.itag && it.sequenceNumber == 102
            })
        } returns observed
        val request = SabrSegmentRequest.media(audio, 100)

        val missing = holder.repositionTargets(listOf(request), playerTimeMs = 995_000L, generation = 0L)

        assertEquals(listOf(request), missing)
    }

    @Test
    fun `reposition advances to the next warmed live segment from inside a boundary`() {
        val audio = sabrFormat(itag = 140, isAudio = true)
        val video = sabrFormat(itag = 248, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { session.getCachedSegment(any()) } returns null
        val holder = holder(session, audio, video)
        val observed = mediaSegment(audio.itag, sequence = 101, startMs = 1_000_000L, durationMs = 5_000L)
        holder.markExpectedLive()
        holder.observeMediaSegment(observed)
        every {
            session.getCachedSegment(match {
                it.format.itag == audio.itag && it.sequenceNumber == 101
            })
        } returns observed
        val request = SabrSegmentRequest.media(audio, 100)

        val missing = holder.repositionTargets(listOf(request), playerTimeMs = 996_200L, generation = 0L)

        assertEquals(emptyList<SabrSegmentRequest>(), missing)
        assertEquals(1_000_000L, holder.readerPosition(audio))
    }

    private fun sabrFormat(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        return format
    }

    private fun mediaSegment(
        itag: Int,
        sequence: Int,
        startMs: Long,
        durationMs: Long = 0L,
    ): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>(relaxed = true)
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        every { header.isInitSegment } returns false
        return mockk { every { this@mockk.header } returns header }
    }

    private fun holder(
        session: YoutubeSabrSession,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ): SabrSessionHolder = SabrSessionHolder(
        session,
        mockk<YoutubeSabrInfo>(),
        audio,
        video,
        "session",
        SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        Instant.now(),
    )
}
