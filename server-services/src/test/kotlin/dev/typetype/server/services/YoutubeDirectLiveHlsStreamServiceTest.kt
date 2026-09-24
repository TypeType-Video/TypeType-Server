package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeDirectLiveHlsStreamServiceTest {
    @Test
    fun returnsLiveWithHlsWithoutCallingFallback() = runTest {
        val liveHlsService = mockk<StreamService>()
        val fallbackService = mockk<StreamService>()
        val response = liveResponse()
        val expected = ExtractionResult.Success(response)
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns expected

        val result = YoutubeDirectLiveHlsStreamService(liveHlsService, fallbackService)
            .getStreamInfo(YOUTUBE_URL)

        assertSame(expected, result)
        coVerify(exactly = 0) { fallbackService.getStreamInfo(YOUTUBE_URL) }
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
        val directFailure = ExtractionResult.Failure("provider blocked", "provider_access_blocked")
        val expected = ExtractionResult.Success(liveResponse())
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns directFailure
        coEvery { fallbackService.getStreamInfo(YOUTUBE_URL) } returns expected

        val result = YoutubeDirectLiveHlsStreamService(liveHlsService, fallbackService)
            .getStreamInfo(YOUTUBE_URL)

        assertSame(expected, result)
    }

    @Test
    fun startsFallbackAfterHedgeDelayAndCancelsSlowDirectAttempt() = runTest {
        val liveHlsService = mockk<StreamService>()
        val fallbackService = mockk<StreamService>()
        val directCancelled = CompletableDeferred<Unit>()
        val fallbackStartedAt = CompletableDeferred<Long>()
        val scheduler = testScheduler
        val expected = ExtractionResult.Success(liveResponse())
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } coAnswers {
            try {
                delay(10_000)
                ExtractionResult.Failure("late direct failure", "provider_access_blocked")
            } finally {
                directCancelled.complete(Unit)
            }
        }
        coEvery { fallbackService.getStreamInfo(YOUTUBE_URL) } coAnswers {
            fallbackStartedAt.complete(scheduler.currentTime)
            delay(150)
            expected
        }

        val result = YoutubeDirectLiveHlsStreamService(liveHlsService, fallbackService)
            .getStreamInfo(YOUTUBE_URL)

        assertSame(expected, result)
        assertEquals(100L, fallbackStartedAt.await())
        assertTrue(directCancelled.isCompleted)
    }

    private fun liveResponse(): StreamResponse = mockk<StreamResponse>().also {
        every { it.isLive } returns true
        every { it.hlsUrl } returns HLS_URL
    }

    private companion object {
        const val VIDEO_ID = "GlzleRbo5E0"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val HLS_URL = "https://manifest.googlevideo.com/live.m3u8"
    }
}
