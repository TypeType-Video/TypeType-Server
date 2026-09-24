package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class YoutubeLiveHlsStreamServiceTest {
    @Test
    fun retriesActiveLiveWithLiveHlsClientWhenMwebHasNoManifest() = runTest {
        val metadataService = mockk<StreamService>()
        val liveHlsService = mockk<StreamService>()
        val metadata = streamResponse(isLive = true, manifestUrl = "")
        val liveHls = streamResponse(isLive = true, manifestUrl = HLS_URL)
        val expected = ExtractionResult.Success(liveHls)
        coEvery { metadataService.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Success(metadata)
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns expected
        val service = YoutubeLiveHlsStreamService(metadataService, liveHlsService)

        assertSame(expected, service.getStreamInfo(YOUTUBE_URL))
        coVerify(exactly = 1) { liveHlsService.getStreamInfo(YOUTUBE_URL) }
    }

    @Test
    fun prefersLiveHlsClientWhenMwebAlreadyHasManifest() = runTest {
        val metadataService = mockk<StreamService>()
        val liveHlsService = mockk<StreamService>()
        val metadata = streamResponse(isLive = true, manifestUrl = "https://mweb.example/live.m3u8")
        val liveHls = streamResponse(isLive = true, manifestUrl = HLS_URL)
        val expected = ExtractionResult.Success(liveHls)
        coEvery { metadataService.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Success(metadata)
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns expected
        val service = YoutubeLiveHlsStreamService(metadataService, liveHlsService)

        assertSame(expected, service.getStreamInfo(YOUTUBE_URL))
        coVerify(exactly = 1) { liveHlsService.getStreamInfo(YOUTUBE_URL) }
    }

    @Test
    fun fallsBackToMwebManifestWhenLiveHlsClientHasNoManifest() = runTest {
        val metadataService = mockk<StreamService>()
        val liveHlsService = mockk<StreamService>()
        val metadata = streamResponse(isLive = true, manifestUrl = "https://mweb.example/live.m3u8")
        val liveHls = streamResponse(isLive = true, manifestUrl = "")
        val expected = ExtractionResult.Success(metadata)
        coEvery { metadataService.getStreamInfo(YOUTUBE_URL) } returns expected
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Success(liveHls)
        val service = YoutubeLiveHlsStreamService(metadataService, liveHlsService)

        assertSame(expected, service.getStreamInfo(YOUTUBE_URL))
        coVerify(exactly = 1) { liveHlsService.getStreamInfo(YOUTUBE_URL) }
    }

    @Test
    fun fallsBackToMwebAfterFastProbeFindsVod() = runTest {
        val metadataService = mockk<StreamService>()
        val liveHlsService = mockk<StreamService>()
        val vod = streamResponse(isLive = false, manifestUrl = "")
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Failure(YOUTUBE_URL)
        val expected = ExtractionResult.Success(vod)
        coEvery { metadataService.getStreamInfo(YOUTUBE_URL) } returns expected
        val service = YoutubeLiveHlsStreamService(metadataService, liveHlsService)

        assertSame(expected, service.getStreamInfo(YOUTUBE_URL))
        coVerify(exactly = 1) { liveHlsService.getStreamInfo(YOUTUBE_URL) }
    }

    private fun streamResponse(isLive: Boolean, manifestUrl: String): StreamResponse {
        val response = mockk<StreamResponse>()
        every { response.isLive } returns isLive
        every { response.hlsUrl } returns manifestUrl
        return response
    }

    private companion object {
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=GlzleRbo5E0"
        const val HLS_URL = "https://manifest.googlevideo.com/live.m3u8"
    }
}
