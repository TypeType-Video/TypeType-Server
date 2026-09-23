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

class SabrBootstrapStreamLiveHlsTest {
    @Test
    fun usesLiveHlsExtractionWithoutPreparingSabr() = runTest {
        val sessionStore = mockk<SabrSessionStore>()
        val tokenClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val liveHlsService = mockk<StreamService>()
        val liveMetadata = mockk<TokenYoutubeSession>()
        every { liveMetadata.isLive } returns true
        val expected = ExtractionResult.Success(mockk<StreamResponse>())
        coEvery { tokenClient.fetchPlaybackSession(VIDEO_ID) } returns liveMetadata
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns expected
        val service = SabrBootstrapStreamService(sessionStore, tokenClient, liveHlsService)

        assertSame(expected, service.getStreamInfo(YOUTUBE_URL))
        coVerify(exactly = 1) { liveHlsService.getStreamInfo(YOUTUBE_URL) }
        coVerify(exactly = 0) { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) }
        coVerify(exactly = 0) { sessionStore.rememberPreparedInfo(VIDEO_ID, any()) }
    }

    private companion object {
        const val VIDEO_ID = "GlzleRbo5E0"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
    }
}
