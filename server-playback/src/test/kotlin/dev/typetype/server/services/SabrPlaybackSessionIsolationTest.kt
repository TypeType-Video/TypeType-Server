package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrPlaybackManifestServiceTest {
    @Test
    fun `unknown segment indexes are retryable preparing`() {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video, endAudio = 0L, endVideo = 0L, maxAudio = 0, maxVideo = 0)

        val result = SabrPlaybackManifestService().build(holder, "/api/sabr/playback/session-token")

        assertEquals(SabrPlaybackManifestResult.Retry("preparing"), result)
    }

    @Test
    fun `unknown segment indexes keep non idle retry status`() {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video, endAudio = 0L, endVideo = 0L, maxAudio = 0, maxVideo = 0)
        holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)

        val result = SabrPlaybackManifestService().build(holder, "/api/sabr/playback/session-token")

        assertEquals(SabrPlaybackManifestResult.Retry("repositioning"), result)
    }

    @Test
    fun `known segment indexes build playback manifest`() {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video, endAudio = 5L, endVideo = 5L, maxAudio = 5, maxVideo = 5)
        holder.setPlayerTimeMs(10_000L)

        val result = SabrPlaybackManifestService().build(holder, "/api/sabr/playback/session-token")

        val ready = result as SabrPlaybackManifestResult.Ready
        assertTrue(ready.manifest.contains("<MPD"))
        assertTrue(ready.manifest.contains("/api/sabr/playback/session-token/137/init?session=session-token"))
        assertTrue(ready.manifest.contains("/api/sabr/playback/session-token/140/segment/2?session=session-token&generation=0"))
    }

    @Test
    fun `buffered edge behind playback start is retryable`() {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video, endAudio = 5L, endVideo = 5L, maxAudio = 5, maxVideo = 5, bufferedEdgeMs = 20_000L)
        holder.setPlayerTimeMs(30_000L)

        val result = SabrPlaybackManifestService().build(holder, "/api/sabr/playback/session-token")

        assertEquals(SabrPlaybackManifestResult.Retry("preparing"), result)
    }

    @Test
    fun `buffered edge at containing segment start is ready`() {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video, endAudio = 5L, endVideo = 5L, maxAudio = 5, maxVideo = 5, bufferedEdgeMs = 30_000L)
        holder.setPlayerTimeMs(35_000L)

        val result = SabrPlaybackManifestService().build(holder, "/api/sabr/playback/session-token")

        assertTrue((result as SabrPlaybackManifestResult.Ready).manifest.contains("<MPD"))
    }

    @Test
    fun `reader tail at containing segment start without buffered edge is retryable`() {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video, endAudio = 5L, endVideo = 5L, maxAudio = 5, maxVideo = 5, bufferedEdgeMs = 20_000L)
        holder.setPlayerTimeMs(35_000L)
        holder.setReaderPosition(audio, 30_000L)
        holder.setReaderPosition(video, 30_000L)

        val result = SabrPlaybackManifestService().build(holder, "/api/sabr/playback/session-token")

        assertEquals(SabrPlaybackManifestResult.Retry("preparing"), result)
    }

    private fun holder(
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        endAudio: Long,
        endVideo: Long,
        maxAudio: Int,
        maxVideo: Int,
        bufferedEdgeMs: Long = 20_000L,
    ): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(true, true) } returns Unit
        every { state.getEndSegment(audio) } returns endAudio
        every { state.getEndSegment(video) } returns endVideo
        every { state.getMaxSegment(audio) } returns maxAudio
        every { state.getMaxSegment(video) } returns maxVideo
        every { state.getMinBufferedEndMs() } returns bufferedEdgeMs
        every { state.getSegmentNumberAtOrAfterTimeMs(any(), any()) } answers {
            ((secondArg<Long>() / 10_000L) + 1L).toInt().coerceIn(1, 5)
        }
        every { state.getSegmentStartMs(any(), any()) } answers { (secondArg<Int>() - 1) * 10_000L }
        every { state.getSegmentEndMs(any(), any()) } answers { secondArg<Int>() * 10_000L }
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
        every { format.isVideo } returns !isAudio
        every { format.mimeType } returns if (isAudio) "audio/mp4; codecs=\"mp4a.40.2\"" else "video/mp4; codecs=\"avc1.640028\""
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        every { format.width } returns if (isAudio) 0 else 1920
        every { format.height } returns if (isAudio) 0 else 1080
        every { format.approxDurationMs } returns 50_000L
        return format
    }
}
