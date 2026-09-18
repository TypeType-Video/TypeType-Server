package dev.typetype.server.routes

import dev.typetype.server.testAudioStream
import dev.typetype.server.testStreamResponse
import dev.typetype.server.testVideoStream
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo

class SabrStreamContractFilterTest {
    @Test
    fun `sabr contract never exposes hls`() {
        val response = testStreamResponse(
            videoOnlyStreams = listOf(
                testVideoStream().copy(
                    url = "",
                    deliveryMethod = "sabr",
                    sabrSessionUrl = "/sabr/session/video-id?videoItag=137",
                ),
            ),
            audioStreams = listOf(
                testAudioStream(
                    url = "",
                    deliveryMethod = "sabr",
                    sabrSessionUrl = "/sabr/session/video-id?audioItag=140",
                ),
            ),
            hlsUrl = "https://example.com/live.m3u8",
        ).copy(isLive = true, isLiveContent = true, hasLiveManifest = true)

        val sabr = response.onlySabrStreams()

        assertEquals("", sabr.hlsUrl)
        assertTrue(sabr.videoOnlyStreams.isNotEmpty())
        assertTrue(sabr.audioStreams.isNotEmpty())
    }

    @Test
    fun `sabr contract exposes every available video codec`() = runTest {
        val h264 = videoFormat(137, "video/mp4; codecs=\"avc1.4d4028\"")
        val vp9 = videoFormat(248, "video/webm; codecs=\"vp9\"")
        val av1 = videoFormat(399, "video/mp4; codecs=\"av01.0.08M.08\"")
        val audio = mockk<YoutubeSabrFormat>(relaxed = true) {
            every { isAudio } returns true
            every { isVideo } returns false
            every { itag } returns 140
            every { mimeType } returns "audio/mp4; codecs=\"mp4a.40.2\""
        }
        val info = mockk<YoutubeSabrInfo> {
            every { formats } returns listOf(h264, vp9, av1, audio)
            every { findFormatByItag(any()) } answers {
                formats.firstOrNull { it.itag == firstArg<Int>() }
            }
        }
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchInfo(VIDEO_ID, cachedFirst = true) } returns SabrPreparedInfo(info, null)
        val response = testStreamResponse(
            videoOnlyStreams = listOf(
                testVideoStream().copy(
                    itag = 137,
                    codec = "avc1.4d4028",
                    deliveryMethod = "sabr",
                    sabrSessionUrl = "/sabr/session/$VIDEO_ID?videoItag=137",
                ),
            ),
            audioStreams = listOf(
                testAudioStream(
                    itag = 140,
                    codec = "mp4a.40.2",
                    deliveryMethod = "sabr",
                    sabrSessionUrl = "/sabr/session/$VIDEO_ID?audioItag=140",
                ),
            ),
        )

        val sabr = response.withPlayableSabrStreams(YOUTUBE_URL, store).onlySabrStreams()

        assertEquals(setOf(137, 248, 399), sabr.videoOnlyStreams.map { it.itag }.toSet())
        assertEquals(setOf("avc1.4d4028", "vp9", "av01.0.08M.08"), sabr.videoOnlyStreams.map { it.codec }.toSet())
    }

    private fun videoFormat(itag: Int, mime: String): YoutubeSabrFormat =
        mockk(relaxed = true) {
            every { isAudio } returns false
            every { isVideo } returns true
            every { this@mockk.itag } returns itag
            every { mimeType } returns mime
            every { height } returns 1080
            every { width } returns 1920
            every { qualityLabel } returns "1080p"
        }

    private companion object {
        const val VIDEO_ID = "X4VbdwhkE10"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
    }
}
