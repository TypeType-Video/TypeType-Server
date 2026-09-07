package dev.typetype.server

import dev.typetype.server.routes.withPlayableSabrStreams
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.SabrTokenBundle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo

class SabrStreamContractFilterTest {
    @Test
    fun `filter removes sabr streams when probe is unavailable`() = runTest {
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchInfo("video1", cachedFirst = true) } returns null
        val progressive = testVideoStream(itag = 18)
        val sabrVideo = testVideoStream(itag = 299).copy(deliveryMethod = "sabr")
        val sabrAudio = testAudioStream(itag = 140, deliveryMethod = "sabr")

        val filtered = testStreamResponse(
            videoOnlyStreams = listOf(progressive, sabrVideo),
            audioStreams = listOf(sabrAudio),
        ).withPlayableSabrStreams("https://youtube.com/watch?v=video1", store)

        assertEquals(listOf(progressive), filtered.videoOnlyStreams)
        assertEquals(emptyList<Any>(), filtered.audioStreams)
    }

    @Test
    fun `filter keeps only sabr streams resolved by sabr info`() = runTest {
        val audio = sabrFormat(itag = 140, isAudio = true, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"")
        val video = sabrFormat(itag = 299, isAudio = false, mimeType = "video/mp4; codecs=\"avc1.64002a\"")
        val info = sabrInfo(listOf(audio, video))
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchInfo("video1", cachedFirst = true) } returns SabrPreparedInfo(info, token())
        val playableVideo = testVideoStream(itag = 299).copy(deliveryMethod = "sabr")
        val missingVideo = testVideoStream(itag = 298).copy(deliveryMethod = "sabr")
        val playableAudio = testAudioStream(itag = 140, deliveryMethod = "sabr")
        val missingAudio = testAudioStream(itag = 141, deliveryMethod = "sabr")

        val filtered = testStreamResponse(
            videoOnlyStreams = listOf(playableVideo, missingVideo),
            audioStreams = listOf(playableAudio, missingAudio),
        ).withPlayableSabrStreams("https://youtube.com/watch?v=video1", store)

        assertEquals(listOf(playableVideo), filtered.videoOnlyStreams)
        assertEquals(listOf(playableAudio), filtered.audioStreams)
    }

    @Test
    fun `filter keeps sabr vp9 and av1 video formats`() = runTest {
        val audio = sabrFormat(itag = 140, isAudio = true, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"")
        val vp9 = sabrFormat(itag = 247, isAudio = false, mimeType = "video/webm; codecs=\"vp09.00.31.08\"")
        val av1 = sabrFormat(itag = 399, isAudio = false, mimeType = "video/mp4; codecs=\"av01.0.08M.08\"")
        val info = sabrInfo(listOf(audio, vp9, av1))
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchInfo("video1", cachedFirst = true) } returns SabrPreparedInfo(info, token())
        val vp9Stream = testVideoStream(itag = 247, codec = "vp09.00.31.08").copy(
            mimeType = "video/webm",
            deliveryMethod = "sabr",
        )
        val av1Stream = testVideoStream(itag = 399, codec = "av01.0.08M.08").copy(deliveryMethod = "sabr")

        val filtered = testStreamResponse(
            videoOnlyStreams = listOf(vp9Stream, av1Stream),
            audioStreams = listOf(testAudioStream(itag = 140, deliveryMethod = "sabr")),
        ).withPlayableSabrStreams("https://youtube.com/watch?v=video1", store)

        assertEquals(listOf(vp9Stream, av1Stream), filtered.videoOnlyStreams)
    }

    @Test
    fun `filter adds sabr video formats present only in sabr info`() = runTest {
        val audio = sabrFormat(itag = 140, isAudio = true, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"")
        val avc = sabrFormat(itag = 137, isAudio = false, mimeType = "video/mp4; codecs=\"avc1.640028\"")
        val vp9 = sabrFormat(itag = 247, isAudio = false, mimeType = "video/webm; codecs=\"vp09.00.31.08\"")
        val av1 = sabrFormat(itag = 399, isAudio = false, mimeType = "video/mp4; codecs=\"av01.0.08M.08\"")
        val info = sabrInfo(listOf(audio, avc, vp9, av1))
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchInfo("video1", cachedFirst = true) } returns SabrPreparedInfo(info, token())
        val avcStream = testVideoStream(itag = 137, codec = "avc1.640028").copy(deliveryMethod = "sabr")

        val filtered = testStreamResponse(
            videoOnlyStreams = listOf(avcStream),
            audioStreams = listOf(testAudioStream(itag = 140, deliveryMethod = "sabr")),
        ).withPlayableSabrStreams("https://youtube.com/watch?v=video1", store)

        assertEquals(listOf(137, 247, 399), filtered.videoOnlyStreams.map { it.itag })
        assertEquals("video/webm", filtered.videoOnlyStreams.first { it.itag == 247 }.mimeType)
        assertEquals("vp09.00.31.08", filtered.videoOnlyStreams.first { it.itag == 247 }.codec)
        assertEquals("av01.0.08M.08", filtered.videoOnlyStreams.first { it.itag == 399 }.codec)
    }

    private fun sabrInfo(formats: List<YoutubeSabrFormat>): YoutubeSabrInfo {
        val info = mockk<YoutubeSabrInfo>()
        every { info.formats } returns formats
        every { info.findFormatByItag(any()) } answers {
            val itag = firstArg<Int>()
            formats.firstOrNull { it.itag == itag }
        }
        return info
    }

    private fun sabrFormat(itag: Int, isAudio: Boolean, mimeType: String): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        every { format.mimeType } returns mimeType
        every { format.audioTrackId } returns null
        every { format.isOriginalAudio } returns true
        every { format.isDrc } returns false
        every { format.xtags } returns null
        every { format.bitrate } returns 128000
        every { format.width } returns if (isAudio) 0 else 1920
        every { format.height } returns if (isAudio) 0 else 1080
        every { format.qualityLabel } returns if (isAudio) null else "1080p"
        every { format.contentLength } returns 1_000_000L
        return format
    }

    private fun token(): SabrTokenBundle = SabrTokenBundle(
        videoId = "video1",
        visitorBoundPoToken = "visitor",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "visitor-data",
        videoBoundPoToken = "video",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
