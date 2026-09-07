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

class SabrLivePlaybackWindowBuilderTest {
    @Test
    fun `active live window never reports end of stream at the current head`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { session.isAtLiveEdge } returns true
        every { streamState.isLive } returns true
        every { streamState.getMaxSegment(audio) } returns 90
        every { streamState.getMaxSegment(video) } returns 100
        every { streamState.getSegmentEndMs(audio, 90) } returns 898_000L
        every { streamState.getSegmentEndMs(video, 100) } returns 897_000L
        every { streamState.liveHeadTimeMs } returns 898_000L
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
            SabrPlaybackWindowRequest(0L, 896_000L, 299, 140, bufferGoalMs = 1_000L),
        )

        assertTrue(result.isReady)
        assertTrue(result.response.live?.active == true)
        assertEquals(898_000L, result.response.durationMs)
        assertEquals(false, result.response.endOfStream)
        assertEquals("/api/sabr/playback/session/140/init?generation=0", result.response.audio.initUrl)
        val videoTrack = requireNotNull(result.response.video)
        assertEquals("/api/sabr/playback/session/299/init?generation=0", videoTrack.initUrl)
    }

    @Test
    fun `live window requests every track missing from the target window`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 248, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { streamState.isLive } returns true
        every { streamState.liveHeadTimeMs } returns 130_000L
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, any()) } returns 100
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, any()) } returns 100
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 248 && request.sequenceNumber == 100 ->
                    cached(248, 100, 100_000L, 2_000L)
                request.format.itag == 140 && request.sequenceNumber in 100..105 ->
                    cached(140, request.sequenceNumber, 100_000L + (request.sequenceNumber - 100) * 2_000L, 2_000L)
                else -> null
            }
        }

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 100_000L, 248, 140, bufferGoalMs = 30_000L),
        )

        assertEquals(listOf(140, 248), result.blockedRequests.map { it.format.itag }.sorted())
        assertEquals(listOf(101, 106), result.blockedRequests.map { it.sequenceNumber })
    }

    @Test
    fun `active live window starts at the shared audio and video timestamp`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 137, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { streamState.isLive } returns true
        every { streamState.liveHeadTimeMs } returns 112_000L
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, any()) } returns 100
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, any()) } returns 100
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 137 && request.sequenceNumber == 100 -> cached(137, 100, 100_002L, 5_000L)
                request.format.itag == 140 && request.sequenceNumber == 100 -> cached(140, 100, 102_010L, 5_000L)
                else -> null
            }
        }

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 100_000L, 137, 140, bufferGoalMs = 1_000L),
        )

        assertTrue(result.isReady)
        assertEquals(102_010L, result.response.startTimeMs)
        assertEquals(102_010L, result.response.audio.segments.single().startMs)
    }

    @Test
    fun `active live startup waits for the full media cushion`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { streamState.isLive } returns true
        every { streamState.liveHeadTimeMs } returns 120_000L
        every { streamState.getSegmentNumberAtOrAfterTimeMs(any(), any()) } returns 50
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            if (request.sequenceNumber in 50..52) {
                cached(request.format.itag, request.sequenceNumber, 100_000L + (request.sequenceNumber - 50) * 2_000L, 2_000L)
            } else {
                null
            }
        }

        val builder = SabrPlaybackWindowBuilder(store)
        val request = SabrPlaybackWindowRequest(0L, 100_000L, 299, 140, bufferGoalMs = 8_000L)
        val startup = builder.build(holder, request)
        val continuation = builder.build(
            holder,
            request.copy(
                bufferedRanges = listOf(
                    SabrPlaybackBufferedRange(140, 0L, 100_000L),
                    SabrPlaybackBufferedRange(299, 0L, 100_000L),
                ),
            ),
        )

        assertFalse(startup.isReady)
        assertTrue(continuation.isReady)
        assertEquals(listOf(53, 53), continuation.blockedRequests.map { it.sequenceNumber })
    }

    @Test
    fun `active live startup derives timing from adjacent cached segments`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { streamState.isLive } returns true
        every { streamState.liveHeadTimeMs } returns 120_000L
        every { streamState.liveHeadSequenceNumber } returns 60L
        every { session.liveHeadSequenceNumber } returns 60L
        every { streamState.getSegmentNumberAtOrAfterTimeMs(any(), any()) } returns 60
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            if (request.sequenceNumber in 50..60) {
                cached(
                    request.format.itag,
                    request.sequenceNumber,
                    100_000L + (request.sequenceNumber - 50) * 2_000L,
                    -1L,
                )
            } else {
                null
            }
        }
        holder.observeMediaSegment(mediaSegment(audio.itag, 60, 120_000L))
        holder.observeMediaSegment(mediaSegment(video.itag, 60, 120_000L))
        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 100_000L, 299, 140, bufferGoalMs = 8_000L),
        )
        assertTrue(result.isReady)
        assertEquals(100_000L, result.response.startTimeMs)
        assertEquals(4, result.response.audio.segments.size)
        assertEquals(List(4) { 2_000L }, result.response.audio.segments.map { it.durationMs })
    }

    @Test
    fun `asymmetric live buffers keep requesting the shorter track`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { streamState.isLive } returns true
        every { streamState.liveHeadTimeMs } returns 70_000L
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, any()) } returns 6
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, any()) } returns 8
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 140 && request.sequenceNumber == 6 -> cached(140, 6, 49_923L, 9_985L)
                request.format.itag == 299 && request.sequenceNumber == 8 -> cached(299, 8, 42_000L, 6_000L)
                else -> null
            }
        }

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(
                0L,
                34_954L,
                299,
                140,
                bufferGoalMs = 8_000L,
                bufferedRanges = listOf(
                    SabrPlaybackBufferedRange(140, 9_984L, 49_923L),
                    SabrPlaybackBufferedRange(299, 24_000L, 42_000L),
                ),
            ),
        )

        assertFalse(result.isReady)
        assertEquals(listOf(299 to 9), result.blockedRequests.map { it.format.itag to it.sequenceNumber })
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

    private fun mediaSegment(itag: Int, sequence: Int, startMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.isInitSegment } returns false
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns -1L
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }
}
