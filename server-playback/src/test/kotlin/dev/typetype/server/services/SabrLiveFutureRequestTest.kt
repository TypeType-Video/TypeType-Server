package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrBufferedRange
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrLiveContinuationRequestTest {
    @Test
    fun `continuation advertises only media served to the player`() {
        val fixture = fixture()
        fixture.holder.observeMediaSegment(segment(fixture.audio.itag, 10_396, 10_395_000L, -1L))
        fixture.holder.observeMediaSegment(segment(fixture.video.itag, 10_396, 10_395_000L, -1L))
        fixture.holder.setLastServedSequence(fixture.audio.itag, 10_392)
        fixture.holder.setLastServedSequence(fixture.video.itag, 10_392)
        fixture.holder.setPlayerTimeMs(10_390_500L)

        val result = withLiveContinuationRequestShape(fixture.holder) { "pumped" }

        assertEquals("pumped", result)
        assertEquals(
            listOf(
                "itag=140:seq=1-10392:time=0+10392000:timescale=1000",
                "itag=299:seq=1-10392:time=0+10392000:timescale=1000",
            ),
            requireNotNull(fixture.rangeOverrides.first()).map(SabrBufferedRange::summarize),
        )
        assertNull(fixture.rangeOverrides.last())
        verify { fixture.state.setPlayerTimeMs(10_390_500L) }
    }

    @Test
    fun `continuation does not advertise an unserved live edge`() {
        val fixture = fixture()
        fixture.holder.observeMediaSegment(segment(fixture.audio.itag, 10_396, 10_395_000L, -1L))
        fixture.holder.observeMediaSegment(segment(fixture.video.itag, 10_396, 10_395_000L, -1L))
        fixture.holder.setPlayerTimeMs(10_390_500L)

        withLiveContinuationRequestShape(fixture.holder) { Unit }

        assertEquals(
            listOf(
                "itag=140:seq=1-10390:time=0+10390000:timescale=1000",
                "itag=299:seq=1-10390:time=0+10390000:timescale=1000",
            ),
            requireNotNull(fixture.rangeOverrides.first()).map(SabrBufferedRange::summarize),
        )
    }

    @Test
    fun `continuation leaves request state unchanged before media is observed`() {
        val fixture = fixture()

        withLiveContinuationRequestShape(fixture.holder) { Unit }

        assertEquals(emptyList<List<SabrBufferedRange>?>(), fixture.rangeOverrides)
    }

    private fun fixture(): Fixture {
        val audio = format(140, audio = true)
        val video = format(299, audio = false)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val rangeOverrides = mutableListOf<List<SabrBufferedRange>?>()
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { session.isAtLiveEdge } returns true
        every { session.liveHeadSequenceNumber } returns 10_400L
        every { state.isLive } returns true
        every { state.isPostLiveDvr } returns false
        every { state.liveHeadSequenceNumber } returns 10_400L
        every { state.liveHeadTimeMs } returns 10_399_000L
        every { state.setBufferedRangesOverride(any()) } answers {
            rangeOverrides += firstArg<List<SabrBufferedRange>?>()
        }
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
        return Fixture(holder, state, audio, video, rangeOverrides)
    }

    private fun format(itag: Int, audio: Boolean): YoutubeSabrFormat = mockk(relaxed = true) {
        every { this@mockk.itag } returns itag
        every { isAudio } returns audio
        every { isVideo } returns !audio
        every { lastModified } returns 1L
    }

    private fun segment(itag: Int, sequence: Int, startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader> {
            every { isInitSegment } returns false
            every { this@mockk.itag } returns itag
            every { sequenceNumber } returns sequence
            every { this@mockk.startMs } returns startMs
            every { this@mockk.durationMs } returns durationMs
        }
        return mockk { every { this@mockk.header } returns header }
    }

    private data class Fixture(
        val holder: SabrSessionHolder,
        val state: YoutubeSabrStreamState,
        val audio: YoutubeSabrFormat,
        val video: YoutubeSabrFormat,
        val rangeOverrides: List<List<SabrBufferedRange>?>,
    )
}
