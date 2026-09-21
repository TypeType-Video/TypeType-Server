package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

class SabrProgressivePlaybackWindowTest {
    @Test
    fun `vod audio starts at the playhead rather than the preceding video keyframe`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { state.getSegmentNumberAtOrAfterTimeMs(video, 100_000L) } returns 16
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 100_000L) } returns 11
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns state
        every { session.getReadableSegment(match { it.format == video && it.sequenceNumber == 16 }) } returns
            readableSegment(video, sequence = 16, startMs = 95_000L, durationMs = 7_000L)
        every { session.getReadableSegment(match { it.format == audio && it.sequenceNumber == 11 }) } returns
            readableSegment(audio, sequence = 11, startMs = 99_845L, durationMs = 9_985L)
        every { session.getCachedSegment(any()) } returns null
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } returns null

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 100_000L, video.itag, audio.itag, bufferGoalMs = 1_000L),
        )

        assertTrue(result.isReady)
        assertEquals(95_000L, requireNotNull(result.response.video).segments.single().startMs)
        assertEquals(99_845L, result.response.audio.segments.single().startMs)
    }

    @Test
    fun `vod window exposes timeline segments before their payload completes`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(308, isAudio = false)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { state.getSegmentNumberAtOrAfterTimeMs(any(), any()) } returns 1
        every { state.getSegmentStartMs(any(), 1) } returns 0L
        every { state.getSegmentEndMs(audio, 1) } returns 9_985L
        every { state.getSegmentEndMs(video, 1) } returns 4_000L
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val audioSegment = readableSegment(audio, durationMs = 9_985L)
        val videoSegment = readableSegment(video, durationMs = 4_000L)
        every { session.streamState } returns state
        every { session.getReadableSegment(match { it.format == audio }) } returns audioSegment
        every { session.getReadableSegment(match { it.format == video }) } returns videoSegment
        every { session.getCachedSegment(any()) } returns null
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } returns null
        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 0L, video.itag, audio.itag, bufferGoalMs = 2_500L),
        )

        assertTrue(result.isReady)
        assertTrue(result.blockedRequests.isEmpty())
        assertFalse(audioSegment.isComplete)
        assertFalse(videoSegment.isComplete)
        assertEquals(4_000L, requireNotNull(result.response.video).segments.single().durationMs)
        assertEquals(9_985L, result.response.audio.segments.single().durationMs)
    }

    @Test
    fun `vod window includes enough progressive segments to satisfy the buffer goal`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { state.getSegmentNumberAtOrAfterTimeMs(any(), 5_731_077L) } returns 1
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns state
        every { session.getCachedSegment(any()) } returns null
        every { session.getReadableSegment(any()) } answers {
            val request = firstArg<SabrSegmentRequest>()
            val durationMs = if (request.format.isAudio) 10_000L else 6_000L
            readableSegment(
                request.format,
                sequence = request.sequenceNumber,
                startMs = 5_731_000L + (request.sequenceNumber - 1L) * durationMs,
                durationMs = durationMs,
            )
        }
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } returns null

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(
                generation = 0L,
                playerTimeMs = 5_731_077L,
                videoItag = video.itag,
                audioItag = audio.itag,
                bufferGoalMs = 30_000L,
            ),
        )

        assertTrue(result.isReady)
        assertTrue(result.blockedRequests.isEmpty())
        assertEquals(6, requireNotNull(result.response.video).segments.size)
        assertEquals(4, result.response.audio.segments.size)
    }

    private fun readableSegment(
        format: YoutubeSabrFormat,
        sequence: Int = 1,
        startMs: Long = 0L,
        durationMs: Long,
    ): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.itag } returns format.itag
        every { header.isInitSegment } returns false
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        return mockk<SabrMediaSegment>().also {
            every { it.header } returns header
            every { it.isComplete } returns false
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
        sessionToken = "session",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat = mockk<YoutubeSabrFormat>().also {
        every { it.itag } returns itag
        every { it.isAudio } returns isAudio
        every { it.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { it.approxDurationMs } returns 900_000L
    }
}
