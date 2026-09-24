package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class YoutubeDirectLiveHlsStreamServiceTest {
    @Test
    fun returnsLiveWithHlsWithoutMetadataRequest() = runTest {
        val liveHlsService = mockk<StreamService>()
        val response = mockk<StreamResponse>()
        every { response.isLive } returns true
        every { response.hlsUrl } returns HLS_URL
        val expected = ExtractionResult.Success(response)
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns expected

        assertSame(expected, YoutubeDirectLiveHlsStreamService(liveHlsService).getStreamInfo(YOUTUBE_URL))
    }

    @Test
    fun rejectsNonLiveResult() = runTest {
        val liveHlsService = mockk<StreamService>()
        val response = mockk<StreamResponse>()
        every { response.isLive } returns false
        every { response.hlsUrl } returns HLS_URL
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Success(response)

        val result = YoutubeDirectLiveHlsStreamService(liveHlsService).getStreamInfo(YOUTUBE_URL)

        assertEquals("live_stream_unavailable", (result as ExtractionResult.Failure).code)
    }

    @Test
    fun usesFallbackWhenDirectLiveExtractionFails() = runTest {
        val liveHlsService = mockk<StreamService>()
        val fallbackService = mockk<StreamService>()
        val response = mockk<StreamResponse>()
        val directFailure = ExtractionResult.Failure("provider blocked", "provider_access_blocked")
        val expected = ExtractionResult.Success(response)
        every { response.isLive } returns true
        every { response.hlsUrl } returns HLS_URL
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns directFailure
        coEvery { fallbackService.getStreamInfo(YOUTUBE_URL) } returns expected

        val result = YoutubeDirectLiveHlsStreamService(liveHlsService, fallbackService)
            .getStreamInfo(YOUTUBE_URL)

        assertSame(expected, result)
    }

    private companion object {
        const val VIDEO_ID = "GlzleRbo5E0"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val HLS_URL = "https://manifest.googlevideo.com/live.m3u8"
    }
}
