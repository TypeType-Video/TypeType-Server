package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.StreamService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManifestRepresentationIdsTest {
    private val streamService: StreamService = mockk()
    private val service = ManifestService(streamService)

    @Test
    fun `manifest representation ids are stable by itag`() = runBlocking {
        val video = testVideoStream(itag = 299)
        val audio = testAudioStream(itag = 251, audioTrackId = "en.0")
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(videoOnlyStreams = listOf(video), audioStreams = listOf(audio)))

        val xml = (service.dashManifest("https://youtube.com/watch?v=test") as ExtractionResult.Success).data

        assertTrue(xml.contains("<Representation id=\"v-299\""))
        assertTrue(xml.contains("<Representation id=\"a-251-en0\""))
    }
}
