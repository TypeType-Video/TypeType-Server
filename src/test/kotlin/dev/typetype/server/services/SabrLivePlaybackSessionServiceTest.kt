package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class SabrLivePlaybackSessionServiceTest {
    @Test
    fun `live start uses the warmed media pair when it is closer to the head`() {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video)
        val state = holder.session.streamState
        every { holder.session.isLive } returns true
        every { state.isLive } returns true
        every { state.isPostLiveDvr } returns false
        every { state.liveHeadTimeMs } returns 1_005_000L
        holder.markExpectedLive()
        holder.observeMediaSegment(mediaSegment(audio.itag, 995_002L))
        holder.observeMediaSegment(mediaSegment(video.itag, 995_000L))

        assertEquals(995_002L, holder.resolvePlaybackStartMs(0L))
    }

    @Test
    fun `live prepare warms metadata and starts behind the live head`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, token(), isLive = true, isLiveContent = true)
        val holder = holder(audio, video)
        val state = holder.session.streamState
        every { holder.session.isLive } returns true
        every { holder.session.liveHeadSequenceNumber } returns 200L
        every { state.isLive } returns true
        every { state.isPostLiveDvr } returns false
        every { state.liveHeadSequenceNumber } returns 200L
        every { state.liveHeadTimeMs } returns 1_005_000L
        every { state.getMaxSegment(audio) } returns 100
        every { state.getMaxSegment(video) } returns 200
        every { state.getSegmentEndMs(audio, 100) } returns 1_000_000L
        every { state.getSegmentEndMs(video, 200) } returns 1_002_000L
        every { state.getBufferedEndMs(audio) } returns 1_000_000L
        every { state.getBufferedEndMs(video) } returns 1_002_000L
        every { state.getMinBufferedEndMs() } returns 1_000_000L
        every { state.getSegmentNumberAtOrAfterTimeMs(video, 985_000L) } returns 198
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 985_000L) } returns 99
        every { state.getSegmentStartMs(video, 198) } returns 990_000L
        every { state.getSegmentStartMs(audio, 99) } returns 990_000L
        val store = mockk<SabrSessionStore>()
        every {
            store.getOrCreate(
                "video",
                "user",
                info,
                audio,
                video,
                prepared.initialToken,
                0L,
                false,
                SabrSessionPurpose.PLAYBACK,
                false,
                0L,
            )
        } returns holder
        coEvery { store.ensureWarmed(holder, 8) } returns Unit
        every { store.startPump(holder) } returns Unit

        val result = SabrPlaybackSessionService(store).prepare("video", "user", prepared, audio, video, 0L)

        assertEquals(985_000L, result.startTimeMs)
        assertEquals(985_000L, holder.playerTimeMs())
        assertTrue(holder.expectsLive())
        verify(exactly = 1) { state.setPlayerTimeMs(9_007_199_254_740_991L) }
        verify(exactly = 1) { state.setWriteTopLevelPlayerTimeMs(false) }
        coVerify(exactly = 1) { store.ensureWarmed(holder, 8) }
        coVerify(exactly = 0) { store.fetchInitializationData(any(), any()) }
        verify(exactly = 1) { store.startPump(holder) }
    }

    @Test
    fun `historical live content prepares as vod`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val replacementVideo = format(248, isAudio = false)
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, token(), isLive = false, isLiveContent = true)
        val holder = holder(audio, video)
        val replacement = holder(audio, replacementVideo, initialGeneration = 1L)
        val state = holder.session.streamState
        val store = mockk<SabrSessionStore>()
        every {
            store.getOrCreate(
                "video",
                "user",
                info,
                audio,
                video,
                prepared.initialToken,
                0L,
                false,
                SabrSessionPurpose.PLAYBACK,
                false,
                0L,
            )
        } returns holder
        every {
            store.getOrCreate(
                "video",
                "user",
                info,
                audio,
                replacementVideo,
                prepared.initialToken,
                30_000L,
                false,
                SabrSessionPurpose.PLAYBACK,
                false,
                1L,
            )
        } returns replacement
        coEvery { store.fetchInitializationData(holder, video) } returns byteArrayOf(1)
        coEvery { store.fetchInitializationData(holder, audio) } returns byteArrayOf(2)
        coEvery { store.fetchInitializationData(replacement, replacementVideo) } returns byteArrayOf(3)
        coEvery { store.fetchInitializationData(replacement, audio) } returns byteArrayOf(4)
        every { store.startPump(holder) } returns Unit
        every { store.startPump(replacement) } returns Unit

        val service = SabrPlaybackSessionService(store)
        val result = service.prepare("video", "user", prepared, audio, video, 0L)
        service.seek(holder, prepared, audio, replacementVideo, 30_000L)

        assertEquals(0L, result.startTimeMs)
        assertFalse(holder.expectsLive())
        assertFalse(replacement.expectsLive())
        verify(exactly = 0) { state.setPlayerTimeMs(9_007_199_254_740_991L) }
        coVerify(exactly = 0) { store.ensureWarmed(any(), any()) }
        coVerify(exactly = 1) { store.fetchInitializationData(holder, video) }
        coVerify(exactly = 1) { store.fetchInitializationData(holder, audio) }
        coVerify(exactly = 1) { store.fetchInitializationData(replacement, replacementVideo) }
        coVerify(exactly = 1) { store.fetchInitializationData(replacement, audio) }
        verify(exactly = 1) { store.startPump(holder) }
        verify(exactly = 1) { store.startPump(replacement) }
    }

    @Test
    fun `live format change starts the replacement session from warmed track boundaries`() = runTest {
        val audio = format(140, isAudio = true)
        val source = holder(audio, format(137, isAudio = false), initialGeneration = 4L)
        val video = format(248, isAudio = false)
        val replacement = holder(audio, video, initialGeneration = 5L)
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, token(), isLive = true, isLiveContent = true)
        val audioSegment = mediaSegment(audio.itag, 995_010L, sequence = 101)
        val videoSegment = mediaSegment(video.itag, 995_000L, sequence = 100)
        replacement.markExpectedLive()
        replacement.observeMediaSegment(audioSegment)
        replacement.observeMediaSegment(videoSegment)
        val replacementState = replacement.session.streamState
        every { replacementState.liveHeadTimeMs } returns 1_005_000L
        every {
            replacement.session.getCachedSegment(match {
                it.format.itag == video.itag && it.sequenceNumber == 100
            })
        } returns videoSegment
        every {
            replacement.session.getCachedSegment(match {
                it.format.itag == audio.itag && it.sequenceNumber == 101
            })
        } returns audioSegment
        val store = mockk<SabrSessionStore>()
        every {
            store.getOrCreate(
                "video",
                "user",
                info,
                audio,
                video,
                prepared.initialToken,
                995_000L,
                false,
                SabrSessionPurpose.PLAYBACK,
                false,
                5L,
            )
        } returns replacement
        coEvery { store.ensureWarmed(replacement, 8) } returns Unit
        every { store.startPump(replacement) } returns Unit

        val result = SabrPlaybackSessionService(store).seek(source, prepared, audio, video, 995_000L)

        assertSame(replacement, result.holder)
        assertEquals(5L, result.holder.activeGeneration())
        assertEquals(audio.itag, result.holder.audioFormat.itag)
        assertEquals(video.itag, result.holder.videoFormat.itag)
        assertNull(replacement.nextSegmentDemand())
        assertEquals(995_010L, replacement.readerPosition(audio))
        assertEquals(1_000_000L, replacement.readerPosition(video))
        assertFalse(replacement.mediaRequestsAt(995_000L).any { it.format.itag == audio.itag })
        assertFalse(replacement.mediaRequestsAt(995_000L).any { it.format.itag == video.itag })
        verify(exactly = 0) { replacementState.setPlayerTimeMs(9_007_199_254_740_991L) }
        verify(exactly = 0) { replacementState.setWriteTopLevelPlayerTimeMs(false) }
        coVerify(exactly = 1) { store.ensureWarmed(replacement, 8) }
        verify(exactly = 1) { store.startPump(replacement) }
    }

    private fun holder(
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        initialGeneration: Long = 0L,
    ): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.getCachedSegment(any()) } returns null
        every { session.getReadableSegment(any()) } returns null
        every { session.isBeyondEnd(any()) } returns false
        every { session.prepareForInitialization(any()) } returns Unit
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.setSelectVideoFormatBeforeAudio(any()) } returns Unit
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token-${sessionIds.incrementAndGet()}",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
            initialGeneration = initialGeneration,
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

    private fun mediaSegment(itag: Int, startMs: Long, sequence: Int = 200): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>(relaxed = true)
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns 5_000L
        every { header.isInitSegment } returns false
        return mockk { every { this@mockk.header } returns header }
    }

    private fun token(): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "visitor-token",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "visitor-data",
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )

    private companion object {
        val sessionIds = AtomicInteger()
    }
}
