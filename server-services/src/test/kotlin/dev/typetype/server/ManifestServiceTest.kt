package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.StreamService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManifestServiceTest {

    private val streamService: StreamService = mockk()
    private val service = ManifestService(streamService)

    @Test
    fun `av01 streams are excluded from manifest`() = runBlocking {
        val av01 = testVideoStream(codec = "av01.0.05M.08")
        val avc = testVideoStream(codec = "avc1.42c01e")
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(videoOnlyStreams = listOf(av01, avc)))

        val result = service.dashManifest("https://youtube.com/watch?v=test")

        assertTrue(result is ExtractionResult.Success)
        val xml = (result as ExtractionResult.Success).data
        assertTrue(!xml.contains("av01"))
        assertTrue(xml.contains("avc1"))
    }

    @Test
    fun `SegmentBase is present when indexStart is positive`() = runBlocking {
        val video = testVideoStream(indexStart = 221, indexEnd = 893, initStart = 0, initEnd = 220)
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(videoOnlyStreams = listOf(video)))

        val result = service.dashManifest("https://youtube.com/watch?v=test")

        val xml = (result as ExtractionResult.Success).data
        assertTrue(xml.contains("SegmentBase"))
        assertTrue(xml.contains("<Representation id=\"v-137\""))
        assertTrue(xml.contains("indexRange=\"221-893\""))
    }

    @Test
    fun `SegmentBase is absent when indexStart is zero`() = runBlocking {
        val video = testVideoStream(indexStart = 0, indexEnd = 0)
        val audio = testAudioStream(indexStart = 0, indexEnd = 0)
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(videoOnlyStreams = listOf(video), audioStreams = listOf(audio)))

        val result = service.dashManifest("https://youtube.com/watch?v=test")

        val xml = (result as ExtractionResult.Success).data
        assertTrue(!xml.contains("SegmentBase"))
    }

    @Test
    fun `vp9 streams are excluded from manifest`() = runBlocking {
        val vp9 = testVideoStream(codec = "vp9", url = "https://example.com/vp9")
        val avc = testVideoStream(codec = "avc1.42c01e", url = "https://example.com/avc")
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(videoOnlyStreams = listOf(vp9, avc)))

        val result = service.dashManifest("https://youtube.com/watch?v=test")

        val xml = (result as ExtractionResult.Success).data
        assertTrue(xml.contains("avc1"))
        assertTrue(!xml.contains("vp9"))
    }

    @Test
    fun `duration appears in mediaPresentationDuration attribute`() = runBlocking {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(duration = 300))

        val result = service.dashManifest("https://youtube.com/watch?v=test")

        val xml = (result as ExtractionResult.Success).data
        assertTrue(xml.contains("PT300S"))
    }

    @Test
    fun `returns Failure when no compatible streams`() = runBlocking {
        val noCodec = testVideoStream(codec = null)
        val blankUrl = testVideoStream(url = "")
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(
                testStreamResponse(
                    videoOnlyStreams = listOf(noCodec, blankUrl),
                    audioStreams = emptyList(),
                )
            )

        val result = service.dashManifest("https://youtube.com/watch?v=test")

        assertTrue(result is ExtractionResult.Failure)
        assertEquals("no_playable_streams", (result as ExtractionResult.Failure).code)
    }

    @Test
    fun `propagates Failure from stream service`() = runBlocking {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Failure("Extraction failed")

        val result = service.dashManifest("https://youtube.com/watch?v=test")

        assertTrue(result is ExtractionResult.Failure)
        assertEquals("Extraction failed", (result as ExtractionResult.Failure).message)
    }

}
