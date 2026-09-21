package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.testStreamResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe

class YoutubePlayerClientFallbackStreamServiceTest {
    @Test
    fun `classic extraction falls back between classic clients only`() = runBlocking {
        NewPipe.setYoutubePlayerClient(YoutubePlayerClient.MWEB.value)
        val observed = mutableListOf<String>()
        val success = ExtractionResult.Success(testStreamResponse().copy(id = YOUTUBE_URL))
        val delegate = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                val client = NewPipe.getYoutubePlayerClient()
                observed += client
                return if (client == YoutubePlayerClient.TV_DOWNGRADED.value) success else ExtractionResult.Failure("blocked")
            }
        }
        val service = YoutubePlayerClientFallbackStreamService(
            delegate,
            listOf(YoutubePlayerClient.VISIONOS, YoutubePlayerClient.TV_DOWNGRADED),
        )

        val result = service.getStreamInfo(YOUTUBE_URL)

        assertSame(success, result)
        assertEquals(
            listOf(YoutubePlayerClient.VISIONOS.value, YoutubePlayerClient.TV_DOWNGRADED.value),
            observed,
        )
        assertEquals(YoutubePlayerClient.MWEB.value, NewPipe.getYoutubePlayerClient())
    }

    @Test
    fun `successful classic client stops fallback chain`() = runBlocking {
        val observed = mutableListOf<String>()
        val success = ExtractionResult.Success(testStreamResponse().copy(id = YOUTUBE_URL))
        val delegate = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                observed += NewPipe.getYoutubePlayerClient()
                return success
            }
        }
        val service = YoutubePlayerClientFallbackStreamService(
            delegate,
            listOf(YoutubePlayerClient.VISIONOS, YoutubePlayerClient.TV_DOWNGRADED),
        )

        val result = service.getStreamInfo(YOUTUBE_URL)

        assertSame(success, result)
        assertEquals(listOf(YoutubePlayerClient.VISIONOS.value), observed)
    }

    private companion object {
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=GlzleRbo5E0"
    }
}
