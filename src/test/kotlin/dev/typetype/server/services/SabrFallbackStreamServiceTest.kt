package dev.typetype.server.services

import dev.typetype.server.testStreamResponse
import dev.typetype.server.models.ExtractionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo

class SabrFallbackStreamServiceTest {
    @Test
    fun `enriches empty youtube extraction with token sabr formats`() = runTest {
        val delegate = mockk<StreamService>()
        val sessionStore = mockk<SabrSessionStore>()
        val tokenSessionClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val response = testStreamResponse(videoOnlyStreams = emptyList(), audioStreams = emptyList())
        coEvery { delegate.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Success(response)
        coEvery { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) } returns preparedInfo()
        val service = SabrFallbackStreamService(delegate, sessionStore, tokenSessionClient)

        val result = service.getStreamInfo(YOUTUBE_URL)

        val enriched = (result as ExtractionResult.Success).data
        assertEquals(listOf(137), enriched.videoOnlyStreams.map { it.itag })
        assertEquals(listOf(140), enriched.audioStreams.map { it.itag })
        assertEquals("sabr", enriched.videoOnlyStreams.single().deliveryMethod)
        assertEquals("sabr", enriched.audioStreams.single().deliveryMethod)
        assertEquals("fr-FR.4", enriched.originalAudioTrackId)
        assertTrue(enriched.audioStreams.single().sabrSessionUrl?.contains("audioTrackId=fr-FR.4") == true)
    }

    @Test
    fun `replaces classic extraction with prepared sabr formats`() = runTest {
        val delegate = mockk<StreamService>()
        val sessionStore = mockk<SabrSessionStore>()
        val tokenSessionClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val response = testStreamResponse()
        coEvery { delegate.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Success(response)
        coEvery { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) } returns preparedInfo()
        val service = SabrFallbackStreamService(delegate, sessionStore, tokenSessionClient)

        val result = service.getStreamInfo(YOUTUBE_URL)

        val enriched = (result as ExtractionResult.Success).data
        assertEquals(listOf(137), enriched.videoOnlyStreams.map { it.itag })
        assertEquals(listOf(140), enriched.audioStreams.map { it.itag })
        assertEquals("sabr", enriched.videoOnlyStreams.single().deliveryMethod)
        assertEquals("sabr", enriched.audioStreams.single().deliveryMethod)
        coVerify(exactly = 1) { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) }
        coVerify(exactly = 0) { tokenSessionClient.fetchPlaybackSession(any()) }
    }

    @Test
    fun `enriches a live hls extraction with prepared sabr formats`() = runTest {
        val delegate = mockk<StreamService>()
        val sessionStore = mockk<SabrSessionStore>()
        val tokenSessionClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val response = testStreamResponse(
            videoOnlyStreams = emptyList(),
            audioStreams = emptyList(),
            hlsUrl = LIVE_HLS_URL,
        ).copy(isLive = true, isLiveContent = true, hasLiveManifest = true, streamType = "live_stream")
        coEvery { delegate.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Success(response)
        coEvery { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) } returns preparedInfo()
        val service = SabrFallbackStreamService(delegate, sessionStore, tokenSessionClient)

        val result = service.getStreamInfo(YOUTUBE_URL)

        val enriched = (result as ExtractionResult.Success).data
        assertEquals(LIVE_HLS_URL, enriched.hlsUrl)
        assertEquals(listOf(137), enriched.videoOnlyStreams.map { it.itag })
        assertEquals(listOf(140), enriched.audioStreams.map { it.itag })
    }

    @Test
    fun `recovers youtube extraction failure with token playback session`() = runTest {
        val delegate = mockk<StreamService>()
        val sessionStore = mockk<SabrSessionStore>()
        val tokenSessionClient = mockk<TypetypeTokenYoutubeSessionClient>()
        coEvery { delegate.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Failure("MWEB player response is not valid")
        coEvery { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) } returns preparedInfo()
        coEvery { tokenSessionClient.fetchPlaybackSession(VIDEO_ID) } returns tokenSession()
        val service = SabrFallbackStreamService(delegate, sessionStore, tokenSessionClient)

        val result = service.getStreamInfo(YOUTUBE_URL)

        val response = (result as ExtractionResult.Success).data
        assertEquals(VIDEO_ID, response.id)
        assertEquals("Fallback title", response.title)
        assertEquals("Fallback channel", response.uploaderName)
        assertEquals("https://example.com/avatar.jpg", response.uploaderAvatarUrl)
        assertEquals(3554L, response.duration)
        assertEquals(listOf(137), response.videoOnlyStreams.map { it.itag })
        assertEquals(listOf(140), response.audioStreams.map { it.itag })
        coVerify(exactly = 1) { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) }
    }

    @Test
    fun `preserves token hls when recovering a live stream`() = runTest {
        val delegate = mockk<StreamService>()
        val sessionStore = mockk<SabrSessionStore>()
        val tokenSessionClient = mockk<TypetypeTokenYoutubeSessionClient>()
        coEvery { delegate.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Failure("blocked")
        coEvery { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) } returns preparedInfo()
        coEvery { tokenSessionClient.fetchPlaybackSession(VIDEO_ID) } returns tokenSession(
            isLive = true,
            hlsUrl = LIVE_HLS_URL,
        )
        val service = SabrFallbackStreamService(delegate, sessionStore, tokenSessionClient)

        val result = service.getStreamInfo(YOUTUBE_URL)

        val response = (result as ExtractionResult.Success).data
        assertEquals(LIVE_HLS_URL, response.hlsUrl)
        assertTrue(response.isLive)
        assertTrue(response.hasLiveManifest)
    }

    private fun preparedInfo(): SabrPreparedInfo {
        val video = mockk<YoutubeSabrFormat>(relaxed = true)
        every { video.isVideo } returns true
        every { video.isAudio } returns false
        every { video.itag } returns 137
        every { video.mimeType } returns "video/mp4; codecs=\"avc1.640028\""
        every { video.qualityLabel } returns "1080p"
        every { video.width } returns 1920
        every { video.height } returns 1080
        every { video.bitrate } returns 4_000_000
        val audio = mockk<YoutubeSabrFormat>(relaxed = true)
        every { audio.isVideo } returns false
        every { audio.isAudio } returns true
        every { audio.itag } returns 140
        every { audio.mimeType } returns "audio/mp4; codecs=\"mp4a.40.2\""
        every { audio.audioQuality } returns "AUDIO_QUALITY_MEDIUM"
        every { audio.audioTrackId } returns "fr-FR.4"
        every { audio.audioTrackDisplayName } returns "French (original)"
        every { audio.isOriginalAudio } returns true
        every { audio.isAudioDefault } returns true
        every { audio.bitrate } returns 129_000
        val info = mockk<YoutubeSabrInfo>()
        every { info.formats } returns listOf(video, audio)
        return SabrPreparedInfo(info, null)
    }

    private fun tokenSession(isLive: Boolean = false, hlsUrl: String = ""): TokenYoutubeSession {
        val prepared = preparedInfo()
        return TokenYoutubeSession(
            info = prepared.info,
            token = null,
            title = "Fallback title",
            author = "Fallback channel",
            channelId = "channel-id",
            channelAvatarUrl = "https://example.com/avatar.jpg",
            description = "Fallback description",
            durationMs = 3_554_000L,
            viewCount = 42L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            tags = listOf("tag"),
            isLive = isLive,
            isLiveContent = isLive,
            hlsUrl = hlsUrl,
        )
    }

    private companion object {
        const val VIDEO_ID = "Vj6ReOur1Kk"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val LIVE_HLS_URL = "https://example.com/live.m3u8"
    }
}
