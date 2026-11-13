package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import dev.typetype.server.sabr.YoutubeSabrInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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
        coEvery { tokenClient.fetchPlaybackSessionResult(VIDEO_ID) } returns TokenServiceResult.Success(liveMetadata)
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns expected
        val service = SabrBootstrapStreamService(sessionStore, tokenClient, liveHlsService)

        assertSame(expected, service.getStreamInfo(YOUTUBE_URL))
        coVerify(exactly = 1) { liveHlsService.getStreamInfo(YOUTUBE_URL) }
        coVerify(exactly = 0) { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) }
        coVerify(exactly = 0) { sessionStore.rememberPreparedInfo(VIDEO_ID, any()) }
    }

    @Test
    fun `a live the live client cannot open plays from the session manifest`() = runTest {
        val tokenClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val liveHlsService = mockk<StreamService>()
        coEvery { tokenClient.fetchPlaybackSessionResult(VIDEO_ID) } returns TokenServiceResult.Success(liveSession())
        coEvery { liveHlsService.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Failure("blocked")
        val service = SabrBootstrapStreamService(mockk<SabrSessionStore>(), tokenClient, liveHlsService)

        val result = service.getStreamInfo(YOUTUBE_URL) as ExtractionResult.Success

        assertTrue(result.data.isLive)
        assertEquals(MANIFEST, result.data.hlsUrl)
    }

    private fun liveSession() = TokenYoutubeSession(
        info = mockk<YoutubeSabrInfo>(relaxed = true),
        token = null,
        title = "live",
        author = "author",
        channelId = "channel",
        channelAvatarUrl = "",
        description = "",
        durationMs = 0L,
        viewCount = 1L,
        thumbnailUrl = "",
        tags = emptyList(),
        isLive = true,
        isLiveContent = true,
        hlsUrl = MANIFEST,
    )

    private companion object {
        const val MANIFEST = "https://manifest.googlevideo.com/api/manifest/hls_variant/file/index.m3u8"
        const val VIDEO_ID = "GlzleRbo5E0"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
    }
}
