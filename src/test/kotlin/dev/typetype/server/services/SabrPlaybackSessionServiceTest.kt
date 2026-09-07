package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class SabrPlaybackSessionServiceTest {
    @AfterEach
    fun clearDemands(): Unit = SabrSegmentDemandTracker.clearAll()

    @Test
    fun `prepare loads timing before selecting target segments`() = runBlocking {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, token())
        val holder = holder(audio, video)
        val initializationFetches = AtomicInteger()
        every { holder.session.streamState.setSelectVideoFormatBeforeAudio(true) } returns Unit
        every { holder.session.streamState.setActiveTrackTypes(any(), any()) } returns Unit
        every { holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(video, 88_168L) } answers {
            assertEquals(2, initializationFetches.get())
            9
        }
        every { holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(audio, 88_168L) } answers {
            assertEquals(2, initializationFetches.get())
            9
        }
        every { holder.session.streamState.getSegmentStartMs(any(), 9) } returns 68_000L
        every { holder.session.streamState.getMinBufferedEndMs() } returns 0L
        every { holder.session.requestNumber } returns 0
        every { holder.session.prepareForForwardJump(any()) } returns Unit
        val store = mockk<SabrSessionStore>()
        every {
            store.getOrCreate(
                "video",
                "user",
                info,
                audio,
                video,
                prepared.initialToken,
                88_168L,
                false,
                SabrSessionPurpose.PLAYBACK,
                false,
                0L,
            )
        } returns holder
        coEvery { store.fetchInitializationData(holder, video) } answers {
            initializationFetches.incrementAndGet()
            byteArrayOf(1)
        }
        coEvery { store.fetchInitializationData(holder, audio) } answers {
            initializationFetches.incrementAndGet()
            byteArrayOf(2)
        }
        every { store.startPump(holder) } returns Unit
        every { store.warmPlaybackAsync(holder) } returns Unit

        val result = SabrPlaybackSessionService(store).prepare("video", "user", prepared, audio, video, 88_168L)

        assertSame(holder, result.holder)
        assertEquals(88_168L, result.startTimeMs)
        assertFalse(result.ready)
        assertEquals(88_168L, holder.playerTimeMs())
        val request = holder.consumeForwardSeek()
        assertEquals(video.itag, request?.format?.itag)
        assertEquals(9, request?.sequenceNumber)
        coVerify(exactly = 0) { store.preflightPlayback(holder, 88_168L) }
        verify(exactly = 0) { holder.session.prepareForInitialization(any()) }
        coVerify(exactly = 1) { store.fetchInitializationData(holder, video) }
        coVerify(exactly = 1) { store.fetchInitializationData(holder, audio) }
        verify { store.startPump(holder) }
        verify(exactly = 0) { store.warmPlaybackAsync(holder) }
    }

    @Test
    fun `missing media segment is retryable without direct fetch`() = runTest {
        val audio = format(140, isAudio = true)
        val holder = holder(audio, format(137, isAudio = false))
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } returns null
        every { store.requestSegmentDemand(holder, any(), 0L) } returns Unit

        val result = SabrPlaybackSessionService(store).fetchMedia(holder, audio, sequence = 4, timeoutMs = 50L, generation = 0L)

        assertEquals(SabrPlaybackSegmentResult.Retry(holder, "repositioning"), result)
        verify(exactly = 1) { store.requestSegmentDemand(holder, any(), 0L) }
    }

    @Test
    fun `readable media segment after demand returns progressive stream`() = runTest {
        val audio = format(140, isAudio = true)
        val holder = holder(audio, format(137, isAudio = false))
        val segment = mockk<SabrMediaSegment>()
        every { holder.session.awaitReadableSegment(any(), any()) } returns segment
        val store = mockk<SabrSessionStore>()
        every { store.requestSegmentDemand(holder, any(), 0L) } returns Unit

        val result = SabrPlaybackSessionService(store).fetchMedia(holder, audio, sequence = 4, timeoutMs = 50L, generation = 0L)

        val stream = result as SabrPlaybackSegmentResult.Stream
        assertEquals("audio/mp4", stream.mimeType)
        assertSame(segment, stream.segment)
        verify(exactly = 1) { store.requestSegmentDemand(holder, any(), 0L) }
    }

    @Test
    fun `stale initialization generation does not fetch`() = runTest {
        val audio = format(140, isAudio = true)
        val holder = holder(audio, format(137, isAudio = false))
        holder.advancePlaybackGeneration(10_000L)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } returns null

        val result = SabrPlaybackSessionService(store).fetchInitialization(holder, audio, timeoutMs = 50L, generation = 0L)

        assertEquals(SabrPlaybackSegmentResult.Stale(holder), result)
        coVerify(exactly = 0) { store.fetchInitializationData(any(), any()) }
    }

    @Test
    fun `invalid media sequence does not call store`() = runTest {
        val audio = format(140, isAudio = true)
        val store = mockk<SabrSessionStore>(relaxed = true)
        val holder = holder(audio, format(137, isAudio = false))

        val result = SabrPlaybackSessionService(store).fetchMedia(holder, audio, sequence = 0, timeoutMs = 50L, generation = 0L)

        assertEquals(SabrPlaybackSegmentResult.InvalidSequence, result)
        verify(exactly = 0) { store.requestSegmentDemand(any(), any(), any()) }
    }

    @Test
    fun `stale media generation does not fetch or reposition`() = runTest {
        val audio = format(140, isAudio = true)
        val holder = holder(audio, format(137, isAudio = false))
        holder.advancePlaybackGeneration(10_000L)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } returns null

        val result = SabrPlaybackSessionService(store).fetchMedia(holder, audio, sequence = 4, timeoutMs = 50L, generation = 0L)

        assertEquals(SabrPlaybackSegmentResult.Stale(holder), result)
        verify(exactly = 0) { store.requestSegmentDemand(any(), any(), any()) }
        assertEquals(null, holder.consumeForwardSeek())
        assertEquals(null, holder.consumeRefetch())
    }

    @Test
    fun `same format seek reuses session and advances generation`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video)
        every { holder.session.streamState.setSelectVideoFormatBeforeAudio(true) } returns Unit
        every { holder.session.streamState.setActiveTrackTypes(any(), any()) } returns Unit
        every { holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(any(), 90_000L) } returns 9
        every { holder.session.streamState.getSegmentStartMs(any(), 9) } returns 70_000L
        every { holder.session.streamState.getMinBufferedEndMs() } returns 10_000L
        val prepared = SabrPreparedInfo(mockk<YoutubeSabrInfo>(), token())
        val store = mockk<SabrSessionStore>()
        every { store.startPump(holder) } returns Unit

        val result = SabrPlaybackSessionService(store).seek(holder, prepared, audio, video, 90_000L)

        assertSame(holder, result.holder)
        assertEquals(1L, holder.activeGeneration())
        assertEquals(70_000L, holder.readerTailMs())
        assertEquals(90_000L, holder.requestedSeekTimeMs())
        val request = holder.consumeForwardSeek()
        assertEquals(video.itag, request?.format?.itag)
        assertEquals(9, request?.sequenceNumber)
        coVerify(exactly = 0) { store.preflightPlayback(any(), any()) }
        verify(exactly = 0) { store.getOrCreate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `same format seek keeps video as paired seek anchor`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video)
        every { holder.session.streamState.setSelectVideoFormatBeforeAudio(true) } returns Unit
        every { holder.session.streamState.setActiveTrackTypes(any(), any()) } returns Unit
        every { holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(video, 120_000L) } returns 24
        every { holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(audio, 120_000L) } returns 13
        every { holder.session.streamState.getSegmentStartMs(video, 24) } returns 119_604L
        every { holder.session.streamState.getSegmentStartMs(audio, 13) } returns 118_979L
        every { holder.session.streamState.getMinBufferedEndMs() } returns 109_637L
        val prepared = SabrPreparedInfo(mockk<YoutubeSabrInfo>(), token())
        val store = mockk<SabrSessionStore>()
        every { store.startPump(holder) } returns Unit

        SabrPlaybackSessionService(store).seek(holder, prepared, audio, video, 120_000L)

        val request = holder.consumeForwardSeek()
        assertEquals(video.itag, request?.format?.itag)
        assertEquals(24, request?.sequenceNumber)
        assertEquals(118_979L, holder.readerTailMs())
    }

    @Test
    fun `audio only switch keeps cached target without rewind`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video)
        every { holder.session.streamState.setActiveTrackTypes(any(), any()) } returns Unit
        every { holder.session.streamState.setSelectVideoFormatBeforeAudio(false) } returns Unit
        every { holder.session.streamState.getSegmentNumberAtOrAfterTimeMs(audio, 299L) } returns 1
        every { holder.session.streamState.getSegmentStartMs(audio, 1) } returns 0L
        every { holder.session.streamState.getSegmentEndMs(audio, 1) } returns 5_000L
        every { holder.session.getCachedSegment(match { it.format.itag == 140 && it.sequenceNumber == 1 }) } returns
            mockk<SabrMediaSegment>()
        val store = mockk<SabrSessionStore>()
        every { store.startPump(holder) } returns Unit

        SabrPlaybackSessionService(store).seekExisting(holder, playerTimeMs = 299L, audioOnly = true)

        assertEquals(1L, holder.activeGeneration())
        assertEquals(5_000L, holder.readerTailMs())
        assertEquals(null, holder.consumeRefetch())
        assertEquals(null, holder.consumeForwardSeek())
        verify { holder.session.streamState.setActiveTrackTypes(false, true) }
    }

    private fun holder(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.getCachedSegment(any()) } returns null
        every { session.getReadableSegment(any()) } returns null
        every { session.awaitReadableSegment(any(), any()) } returns null
        every { session.isBeyondEnd(any()) } returns false
        every { session.prepareForInitialization(any()) } returns Unit
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.setSelectVideoFormatBeforeAudio(any()) } returns Unit
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
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        return format
    }

    private fun cachedSegment(itag: Int, sequence: Int, bytes: ByteArray): CachedSabrSegment = CachedSabrSegment(
        itag = itag,
        sequence = sequence,
        init = false,
        startMs = sequence * 1_000L,
        durationMs = 1_000L,
        mimeType = "audio/mp4",
        bytesBase64 = Base64.getEncoder().encodeToString(bytes),
        byteLength = bytes.size,
    )

    private fun token(): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "visitor-token",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "visitor-data",
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
