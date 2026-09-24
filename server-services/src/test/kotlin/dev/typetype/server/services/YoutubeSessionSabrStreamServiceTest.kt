package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoutubeSessionSabrStreamServiceTest {
    @Test
    fun `returns null for live metadata so the public HLS path is used`() = runTest {
        val metadataService = mockk<YoutubeSessionStreamService>()
        val infoService = mockk<AuthenticatedSabrInfoService>()
        val live = mockk<StreamResponse>()
        every { live.isLive } returns true
        val expected = ExtractionResult.Success(live)
        coEvery { metadataService.getStreamInfo(USER_ID, YOUTUBE_URL) } returns expected
        val service = YoutubeSessionSabrStreamService(metadataService, infoService)

        assertEquals(null, service.getStreamInfo(USER_ID, YOUTUBE_URL))
        coVerify(exactly = 0) { infoService.fetch(USER_ID, VIDEO_ID) }
    }

    private companion object {
        const val USER_ID = "user-id"
        const val VIDEO_ID = "GlzleRbo5E0"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
    }
}
