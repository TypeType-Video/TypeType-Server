package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo

class SabrBootstrapStreamServiceTest {
    @Test
    fun `returns playable token formats after sabr preparation`() = runTest {
        val sessionStore = mockk<SabrSessionStore>()
        val tokenClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val prepared = preparedInfo()
        val session = tokenSession(prepared.info, tokenBundle())
        coEvery { sessionStore.rememberPreparedInfo(VIDEO_ID, any()) } returns Unit
        coEvery { tokenClient.fetchPlaybackSession(VIDEO_ID) } returns session
        val service = SabrBootstrapStreamService(sessionStore, tokenClient)

        val result = service.getStreamInfo(YOUTUBE_URL)

        val response = (result as ExtractionResult.Success).data
        assertEquals("Bootstrap title", response.title)
        assertEquals(listOf(137), response.videoOnlyStreams.map { it.itag })
        assertEquals(listOf(140), response.audioStreams.map { it.itag })
        coVerify(exactly = 0) { sessionStore.fetchInfo(any(), any()) }
        coVerify(exactly = 1) { sessionStore.rememberPreparedInfo(VIDEO_ID, any()) }
        coVerify(exactly = 1) { tokenClient.fetchPlaybackSession(VIDEO_ID) }
    }

    @Test
    fun `never falls back when sabr preparation fails`() = runTest {
        val sessionStore = mockk<SabrSessionStore>()
        val tokenClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val prepared = preparedInfo()
        coEvery { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) } returns null
        coEvery { tokenClient.fetchPlaybackSession(VIDEO_ID) } returns tokenSession(prepared.info)
        val service = SabrBootstrapStreamService(sessionStore, tokenClient)

        val result = service.getStreamInfo(YOUTUBE_URL)

        assertEquals(ExtractionResult.Failure("SABR playback formats unavailable"), result)
    }

    private fun preparedInfo(): SabrPreparedInfo {
        val video = mockk<YoutubeSabrFormat>(relaxed = true)
        every { video.isVideo } returns true
        every { video.itag } returns 137
        every { video.mimeType } returns "video/mp4; codecs=\"avc1.640028\""
        every { video.qualityLabel } returns "1080p"
        val audio = mockk<YoutubeSabrFormat>(relaxed = true)
        every { audio.isAudio } returns true
        every { audio.itag } returns 140
        every { audio.mimeType } returns "audio/mp4; codecs=\"mp4a.40.2\""
        every { audio.audioTrackId } returns "en.4"
        val info = mockk<YoutubeSabrInfo>()
        every { info.formats } returns listOf(video, audio)
        every { info.visitorData } returns VISITOR_DATA
        return SabrPreparedInfo(info, null)
    }

    private fun tokenBundle() = SabrTokenBundle(
        videoId = VIDEO_ID,
        visitorBoundPoToken = "player-token",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = VISITOR_DATA,
        videoBoundPoToken = "media-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )

    private fun tokenSession(info: YoutubeSabrInfo, token: SabrTokenBundle? = null): TokenYoutubeSession = TokenYoutubeSession(
        info = info,
        token = token,
        title = "Bootstrap title",
        author = "Bootstrap channel",
        channelId = "channel-id",
        channelAvatarUrl = "https://example.com/avatar.jpg",
        description = "Bootstrap description",
        durationMs = 60_000L,
        viewCount = 42L,
        thumbnailUrl = "https://example.com/thumb.jpg",
        tags = emptyList(),
        isLive = false,
        isLiveContent = false,
    )

    private companion object {
        const val VIDEO_ID = "f6f3PhauXyg"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val VISITOR_DATA = "visitor-data"
    }
}
