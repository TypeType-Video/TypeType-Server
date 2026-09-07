package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrStreamState

class SabrDashManifestBuilderTest {
    @Test
    fun `dash manifest uses manifest relative sabr media urls`() {
        val audio = format(140, "audio/mp4; codecs=\"mp4a.40.2\"", isAudio = true)
        val video = format(137, "video/mp4; codecs=\"avc1.640028\"", isAudio = false)
        val state = streamState(audio, video)

        val manifest = SabrDashManifestBuilder.build(
            "dHqmN-5jVKY",
            audio,
            video,
            endSegmentAudio = 2,
            endSegmentVideo = 2,
            streamState = state,
            sessionToken = "session-token",
        )

        assertTrue(manifest.contains("sourceURL=\"../dHqmN-5jVKY/137/init?session=session-token\""))
        assertTrue(manifest.contains("media=\"../dHqmN-5jVKY/140/segment/1?session=session-token\""))
        assertFalse(manifest.contains("/api/sabr/dHqmN-5jVKY"))
    }

    @Test
    fun `dash manifest can start at seek segment`() {
        val audio = format(140, "audio/mp4; codecs=\"mp4a.40.2\"", isAudio = true)
        val video = format(137, "video/mp4; codecs=\"avc1.640028\"", isAudio = false)
        val state = streamState(audio, video)

        val manifest = SabrDashManifestBuilder.build(
            "tRDCFwgcN0s",
            audio,
            video,
            endSegmentAudio = 4,
            endSegmentVideo = 4,
            streamState = state,
            sessionToken = "session-token",
            startSegmentAudio = 3,
            startSegmentVideo = 4,
        )

        assertFalse(manifest.contains("../tRDCFwgcN0s/137/segment/3?"))
        assertTrue(manifest.contains("../tRDCFwgcN0s/137/segment/4?session=session-token"))
        assertFalse(manifest.contains("../tRDCFwgcN0s/140/segment/2?"))
        assertTrue(manifest.contains("../tRDCFwgcN0s/140/segment/3?session=session-token"))
    }

    @Test
    fun `dash manifest can use playback session media path`() {
        val audio = format(140, "audio/mp4; codecs=\"mp4a.40.2\"", isAudio = true)
        val video = format(137, "video/mp4; codecs=\"avc1.640028\"", isAudio = false)
        val state = streamState(audio, video)

        val manifest = SabrDashManifestBuilder.build(
            "tRDCFwgcN0s",
            audio,
            video,
            endSegmentAudio = 2,
            endSegmentVideo = 2,
            streamState = state,
            sessionToken = "session-token",
            mediaBasePath = "/api/sabr/playback/session-token",
        )

        assertTrue(manifest.contains("sourceURL=\"/api/sabr/playback/session-token/137/init?session=session-token\""))
        assertTrue(manifest.contains("media=\"/api/sabr/playback/session-token/140/segment/1?session=session-token\""))
        assertFalse(manifest.contains("/api/sabr/tRDCFwgcN0s/"))
    }

    private fun format(itag: Int, mime: String, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.mimeType } returns mime
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        every { format.width } returns if (isAudio) 0 else 1920
        every { format.height } returns if (isAudio) 0 else 1080
        every { format.approxDurationMs } returns 2_000L
        return format
    }

    private fun streamState(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): YoutubeSabrStreamState {
        val state = mockk<YoutubeSabrStreamState>()
        every { state.getEndSegment(video) } returns 2L
        every { state.getSegmentEndMs(video, 2) } returns 2_000L
        listOf(audio, video).forEach { format ->
            every { state.getSegmentStartMs(format, 1) } returns 0L
            every { state.getSegmentEndMs(format, 1) } returns 1_000L
            every { state.getSegmentStartMs(format, 2) } returns 1_000L
            every { state.getSegmentEndMs(format, 2) } returns 2_000L
            every { state.getSegmentStartMs(format, 3) } returns 2_000L
            every { state.getSegmentEndMs(format, 3) } returns 3_000L
            every { state.getSegmentStartMs(format, 4) } returns 3_000L
            every { state.getSegmentEndMs(format, 4) } returns 4_000L
        }
        return state
    }
}
