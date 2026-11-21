package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.testStreamResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SabrFallbackStreamMapperTest {

    @Test
    fun `fallback audio reports kbps like extracted audio while video keeps bps`() {
        val info = mockk<YoutubeSabrInfo> {
            every { formats } returns listOf(
                audioFormat(140, 130_252),
                audioFormat(599, 33_431),
                videoFormat(137, 4_400_000),
            )
        }

        val response = testStreamResponse().withSabrFallback("video", info)

        assertEquals(listOf(130, 33), response.audioStreams.map { it.bitrate })
        assertEquals(listOf(4_400_000), response.videoOnlyStreams.map { it.bitrate })
    }

    private fun audioFormat(itag: Int, bps: Int): YoutubeSabrFormat = mockk(relaxed = true) {
        every { isAudio } returns true
        every { isVideo } returns false
        every { this@mockk.itag } returns itag
        every { mimeType } returns "audio/mp4; codecs=\"mp4a.40.2\""
        every { bitrate } returns bps
        every { audioTrackId } returns null
    }

    private fun videoFormat(itag: Int, bps: Int): YoutubeSabrFormat = mockk(relaxed = true) {
        every { isAudio } returns false
        every { isVideo } returns true
        every { this@mockk.itag } returns itag
        every { mimeType } returns "video/mp4; codecs=\"avc1.640028\""
        every { bitrate } returns bps
        every { qualityLabel } returns "1080p"
    }
}
