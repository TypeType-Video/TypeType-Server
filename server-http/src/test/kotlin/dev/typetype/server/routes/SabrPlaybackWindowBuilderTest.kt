package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

class SabrPlaybackWindowBuilderTest {
    @Test
    fun `window rebases stale predicted sequence from media header timing`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 491_203L) } returns 98
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, 491_203L) } returns 49
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.format.itag == 299 && it.sequenceNumber == 101 }
                ?.let { mediaSegment(sequence = 101, startMs = 488_200L, durationMs = 6_500L) }
        }
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 299 && request.sequenceNumber == 98 -> cached(299, 98, 474_000L, 4_000L)
                request.format.itag == 299 && request.sequenceNumber == 101 -> cached(299, 101, 488_200L, 6_500L)
                request.format.itag == 140 && request.sequenceNumber == 49 -> cached(140, 49, 479_259L, 9_985L)
                request.format.itag == 140 && request.sequenceNumber == 50 -> cached(140, 50, 489_244L, 9_985L)
                else -> null
            }
        }
        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 491_203L, 299, 140, bufferGoalMs = 1_000L),
        )

        assertTrue(result.isReady)
        val videoSegment = requireNotNull(result.response.video).segments.single()
        assertEquals("/api/sabr/playback/session/299/segment/101?generation=0", videoSegment.url)
        assertEquals(488_200L, videoSegment.startMs)
        verify(exactly = 1) { streamState.jumpBufferedTo(video, 101) }
    }

    @Test
    fun `live window ignores client ranges before serving its own media`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 401, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { streamState.isLive } returns true
        every { streamState.liveHeadTimeMs } returns 330_000L
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 300_000L) } returns 60
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, 296_600L) } returns 30
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 401 && request.sequenceNumber == 60 -> cached(401, 60, 296_600L, 6_900L)
                request.format.itag == 140 && request.sequenceNumber == 30 -> cached(140, 30, 289_552L, 9_985L)
                request.format.itag == 140 && request.sequenceNumber == 31 -> cached(140, 31, 299_537L, 9_985L)
                else -> null
            }
        }
        val ranges = listOf(SabrPlaybackBufferedRange(401, 290_000L, 320_000L))
        val request = SabrPlaybackWindowRequest(0L, 300_000L, 401, 140, bufferGoalMs = 30_000L, bufferedRanges = ranges)

        assertTrue(SabrPlaybackWindowBuilder(store).build(holder, request).isReady)
    }

    @Test
    fun `window skips missing sequence when next segment is time contiguous`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 315, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 180_000L) } returns 36
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, 179_312L) } returns 18
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.format.itag == 315 && it.sequenceNumber == 41 }
                ?.let { mediaSegment(sequence = 41, startMs = 202_302L, durationMs = 5_672L) }
        }
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 315 && request.sequenceNumber in 36..39 ->
                    cached(315, request.sequenceNumber, 179_312L + (request.sequenceNumber - 36) * 5_747L, 5_747L)
                request.format.itag == 315 && request.sequenceNumber == 41 -> cached(315, 41, 202_302L, 5_672L)
                request.format.itag == 140 && request.sequenceNumber in 18..21 ->
                    cached(140, request.sequenceNumber, 169_738L + (request.sequenceNumber - 18) * 9_985L, 9_985L)
                else -> null
            }
        }

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 180_000L, 315, 140, bufferGoalMs = 25_000L),
        )

        assertEquals(listOf(36, 37, 38, 39, 41), requireNotNull(result.response.video).segments.map { it.url.sequenceFromUrl() })
        verify(exactly = 1) { streamState.jumpBufferedTo(video, 41) }
    }

    @Test
    fun `window continues after client buffered edge without refetching played media`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 315, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 209_676L) } returns 41
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, 209_676L) } returns 22
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 315 && request.sequenceNumber == 41 -> cached(315, 41, 209_676L, 5_672L)
                request.format.itag == 315 && request.sequenceNumber == 42 -> cached(315, 42, 215_348L, 5_672L)
                request.format.itag == 140 && request.sequenceNumber == 22 -> cached(140, 22, 209_676L, 9_985L)
                else -> null
            }
        }
        val ranges = listOf(
            SabrPlaybackBufferedRange(315, 179_312L, 209_676L),
            SabrPlaybackBufferedRange(140, 179_312L, 209_676L),
        )

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(
                generation = 0L,
                playerTimeMs = 187_000L,
                videoItag = 315,
                audioItag = 140,
                bufferGoalMs = 30_000L,
                backBufferMs = 10_000L,
                bufferedRanges = ranges,
            ),
        )

        assertEquals(41, requireNotNull(result.response.video).segments.first().url.sequenceFromUrl())
        assertEquals(22, result.response.audio.segments.first().url.sequenceFromUrl())
        coVerify(exactly = 0) {
            store.cachedSegment(holder, match { !it.isInitializationSegment && it.sequenceNumber < 22 })
        }
    }

    @Test
    fun `live window continues after the last segment served on each track`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { session.liveHeadSequenceNumber } returns 45L
        every { streamState.isLive } returns true
        every { streamState.liveHeadSequenceNumber } returns 45L
        every { streamState.liveHeadTimeMs } returns 220_000L
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, any()) } returns 43
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, any()) } returns 23
        val holder = holder(session, audio, video)
        holder.setLastServedSequence(video.itag, 41)
        holder.setLastServedSequence(audio.itag, 21)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } returns null
        val ranges = listOf(
            SabrPlaybackBufferedRange(video.itag, 190_000L, 208_000L),
            SabrPlaybackBufferedRange(audio.itag, 190_000L, 208_000L),
        )

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 200_000L, video.itag, audio.itag, bufferGoalMs = 8_000L, bufferedRanges = ranges),
        )

        assertEquals(42, result.blockedRequests.first { !it.format.isAudio }.sequenceNumber)
        assertEquals(22, result.blockedRequests.first { it.format.isAudio }.sequenceNumber)
    }

    @Test
    fun `final indexed segments complete the window without requesting beyond end`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { streamState.getEndSegment(audio) } returns 90L
        every { streamState.getEndSegment(video) } returns 100L
        every { streamState.getSegmentEndMs(audio, 90) } returns 898_000L
        every { streamState.getSegmentEndMs(video, 100) } returns 897_000L
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, any()) } returns 90
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, any()) } returns 100
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when (request.format.itag) {
                140 -> cached(140, 90, 888_000L, 10_000L)
                else -> cached(299, 100, 892_000L, 5_000L)
            }
        }

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 896_000L, 299, 140, bufferGoalMs = 30_000L),
        )
        assertTrue(result.isReady)
        assertTrue(result.response.endOfStream)
        assertEquals(898_000L, result.response.durationMs)
        coVerify(exactly = 0) { store.cachedSegment(holder, match { it.sequenceNumber > 100 }) }
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
        key = SabrSessionKey("video", "user", 140, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { format.approxDurationMs } returns 900_000L
        return format
    }

    private fun mediaSegment(sequence: Int, startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
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

    private fun String.sequenceFromUrl(): Int = substringAfter("/segment/").substringBefore('?').toInt()
}
