package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrLiveGapWindowTest {
    @Test
    fun `small live gap extends the current playback window`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.liveHeadTimeMs } returns 600_000L
        every { state.getSegmentStartMs(video, 92) } returns 475_000L
        val holder = holder(session, audio, video)
        holder.setLastServedSequence(video.itag, 91)
        holder.setLastServedSequence(audio.itag, 48)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 299 && request.sequenceNumber == 93 -> cached(299, 93, 480_000L, 5_000L)
                request.format.itag == 299 && request.sequenceNumber == 94 -> cached(299, 94, 485_000L, 5_000L)
                request.format.itag == 140 && request.sequenceNumber == 49 -> cached(140, 49, 479_259L, 9_985L)
                else -> null
            }
        }
        val ranges = listOf(
            SabrPlaybackBufferedRange(video.itag, 450_000L, 475_000L),
            SabrPlaybackBufferedRange(audio.itag, 450_000L, 479_259L),
        )

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 470_000L, 299, 140, bufferGoalMs = 8_000L, bufferedRanges = ranges),
        )

        assertTrue(result.isReady)
        assertEquals(480_000L, result.response.startTimeMs)
        assertEquals(
            listOf(
                "/api/sabr/playback/session/299/segment/93?generation=0",
                "/api/sabr/playback/session/299/segment/94?generation=0",
            ),
            requireNotNull(result.response.video).segments.map { it.url },
        )
        assertNull(holder.terminalFailure())
    }

    @Test
    fun `small live gap keeps the current session when playhead remains behind`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.liveHeadTimeMs } returns 510_000L
        every { state.getSegmentNumberAtOrAfterTimeMs(video, any()) } returns 92
        val holder = holder(session, audio, video)
        holder.setLastServedSequence(video.itag, 93)
        holder.setLastServedSequence(audio.itag, 49)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 299 && request.sequenceNumber in 94..96 ->
                    cached(299, request.sequenceNumber, 485_000L + (request.sequenceNumber - 94) * 5_000L, 5_000L)
                request.format.itag == 140 && request.sequenceNumber in 50..51 ->
                    cached(140, request.sequenceNumber, 489_244L + (request.sequenceNumber - 50) * 9_985L, 9_985L)
                else -> null
            }
        }
        val ranges = listOf(
            SabrPlaybackBufferedRange(video.itag, 450_000L, 477_000L),
            SabrPlaybackBufferedRange(video.itag, 480_000L, 485_000L),
            SabrPlaybackBufferedRange(audio.itag, 450_000L, 489_244L),
        )

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 477_942L, 299, 140, bufferGoalMs = 8_000L, bufferedRanges = ranges),
        )

        assertTrue(result.isReady)
        assertFalse(result.blockedRequests.any { it.sequenceNumber == 92 })
        assertEquals(
            "/api/sabr/playback/session/299/segment/94?generation=0",
            requireNotNull(result.response.video).segments.first().url,
        )
        assertEquals(
            "/api/sabr/playback/session/140/segment/50?generation=0",
            result.response.audio.segments.first().url,
        )
        assertNull(holder.terminalFailure())
    }

    @Test
    fun `advancing live head does not replace the next playback segment`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.liveHeadTimeMs } returns 510_000L
        val holder = holder(session, audio, video)
        holder.setLastServedSequence(video.itag, 91)
        holder.setLastServedSequence(audio.itag, 48)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 299 && request.sequenceNumber == 99 ->
                    cached(299, 99, 483_000L, 1_000L)
                request.format.itag == 140 && request.sequenceNumber == 56 ->
                    cached(140, 56, 483_000L, 1_000L)
                else -> null
            }
        }
        val ranges = listOf(
            SabrPlaybackBufferedRange(video.itag, 450_000L, 475_000L),
            SabrPlaybackBufferedRange(audio.itag, 450_000L, 475_000L),
        )

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 474_000L, 299, 140, bufferGoalMs = 8_000L, bufferedRanges = ranges),
        )

        assertFalse(result.isReady)
        assertEquals(
            listOf(299 to 92, 140 to 49),
            result.blockedRequests.map { it.format.itag to it.sequenceNumber },
        )
        assertNull(holder.terminalFailure())
    }

    @Test
    fun `large live gap requests a fresh playback session`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.liveHeadTimeMs } returns 510_000L
        every { state.getSegmentStartMs(video, 92) } returns 475_000L
        val holder = holder(session, audio, video)
        holder.setLastServedSequence(video.itag, 91)
        holder.setLastServedSequence(audio.itag, 48)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 299 && request.sequenceNumber == 114 -> cached(299, 114, 585_000L, 5_000L)
                request.format.itag == 140 && request.sequenceNumber == 49 -> cached(140, 49, 479_259L, 9_985L)
                else -> null
            }
        }
        val ranges = listOf(
            SabrPlaybackBufferedRange(video.itag, 450_000L, 475_000L),
            SabrPlaybackBufferedRange(audio.itag, 450_000L, 479_259L),
        )

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 470_000L, 299, 140, bufferGoalMs = 8_000L, bufferedRanges = ranges),
        )

        assertFalse(result.isReady)
        assertEquals("SABR recoverable failure: live 299 media discontinuity", holder.terminalFailure())
        assertNull(result.blockedRequests.firstOrNull { it.format.itag == video.itag })
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

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat = mockk<YoutubeSabrFormat>(relaxed = true) {
        every { this@mockk.itag } returns itag
        every { this@mockk.isAudio } returns isAudio
        every { mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
    }

    private fun cached(itag: Int, sequence: Int, startMs: Long, durationMs: Long): CachedSabrSegment = CachedSabrSegment(
        itag = itag,
        sequence = sequence,
        init = false,
        startMs = startMs,
        durationMs = durationMs,
        mimeType = if (itag == 140) "audio/mp4" else "video/mp4",
        bytesBase64 = "AA==",
        byteLength = 1,
    )
}
