package dev.typetype.server.services

import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.time.Duration

class SabrPreparedInfoCacheTest {
    @Test
    fun `prepared info reports audio and video only when both are present`() {
        assertFalse(preparedInfo(listOf(format(isAudio = false))).hasAudioAndVideoFormats())
        assertFalse(preparedInfo(listOf(format(isAudio = true))).hasAudioAndVideoFormats())
        assertTrue(preparedInfo(listOf(format(isAudio = true), format(isAudio = false))).hasAudioAndVideoFormats())
    }

    @Test
    fun `cache can remove incomplete prepared info`() {
        val cache = SabrPreparedInfoCache()
        cache.put("video", 120_000L, preparedInfo(listOf(format(isAudio = false))))

        cache.remove("video", 120_000L)

        assertFalse(cache.get("video", 120_000L)?.hasAudioAndVideoFormats() == true)
    }

    @Test
    fun `cache separates prepared info by start window`() {
        val cache = SabrPreparedInfoCache()
        cache.put("video", 120_000L, preparedInfo(listOf(format(isAudio = true), format(isAudio = false))))

        assertTrue(cache.get("video", 120_000L)?.hasAudioAndVideoFormats() == true)
        assertFalse(cache.get("video", 340_000L)?.hasAudioAndVideoFormats() == true)
    }

    @Test
    fun `cache removes every start window for a video`() {
        val cache = SabrPreparedInfoCache()
        val prepared = preparedInfo(listOf(format(isAudio = true), format(isAudio = false)))
        cache.put("video", 120_000L, prepared)
        cache.put("video", 340_000L, prepared)

        cache.remove("video")

        assertFalse(cache.get("video", 120_000L)?.hasAudioAndVideoFormats() == true)
        assertFalse(cache.get("video", 340_000L)?.hasAudioAndVideoFormats() == true)
    }

    @Test
    fun `cache evicts expired entries without reading their keys`() {
        var now = 0L
        val cache = SabrPreparedInfoCache(
            ttl = Duration.ofMillis(10),
            clock = { now },
        )
        cache.put("video", 0L, preparedInfo(listOf(format(true), format(false))))
        now = 10L

        cache.evictExpired()

        assertFalse(cache.get("video", 0L)?.hasAudioAndVideoFormats() == true)
    }

    @Test
    fun `cache limits retained preparation windows`() {
        val cache = SabrPreparedInfoCache(maxEntries = 1)
        val prepared = preparedInfo(listOf(format(true), format(false)))
        cache.put("first", 0L, prepared)

        cache.put("second", 0L, prepared)

        assertFalse(cache.get("first", 0L)?.hasAudioAndVideoFormats() == true)
        assertSame(prepared, cache.get("second", 0L))
    }

    @Test
    fun `info fetcher keeps extracted sabr info for initialization fallback`() = runTest {
        val audio = format(isAudio = true)
        val video = format(isAudio = false)
        val info = info(listOf(audio, video))
        val fetcher = SabrInfoFetcher(mockk(relaxed = true))

        fetcher.rememberExtractedInfo("video", info)

        assertSame(video, fetcher.initializationFormat("video", video))
    }

    @Test
    fun `info fetcher consumes token session metadata and tokens atomically`() = runTest {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val sessionClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val info = info(listOf(format(isAudio = true), format(isAudio = false)))
        val token = token()
        coEvery { sessionClient.fetchPlaybackSession("video") } returns tokenSession(info, token)
        val fetcher = SabrInfoFetcher(tokenClient, sessionClient)

        val result = fetcher.fetchInfo("video")

        assertEquals(info.formats, result?.info?.formats)
        assertSame(token, result?.initialToken)
        coVerify(exactly = 1) { sessionClient.fetchPlaybackSession("video") }
        verify(exactly = 0) { tokenClient.fetch(any(), any(), any()) }
    }

    @Test
    fun `info fetcher accepts legacy token session only with matching visitor data`() = runTest {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val sessionClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val info = info(listOf(format(isAudio = true), format(isAudio = false)))
        val token = token()
        coEvery { sessionClient.fetchPlaybackSession("video") } returns tokenSession(info, null)
        every { tokenClient.fetch("video", forceRefresh = false, refreshVideo = false) } returns token
        val fetcher = SabrInfoFetcher(tokenClient, sessionClient)

        val result = fetcher.fetchInfo("video")

        assertEquals(info.formats, result?.info?.formats)
        assertSame(token, result?.initialToken)
        verify(exactly = 1) { tokenClient.fetch("video", forceRefresh = false, refreshVideo = false) }
    }

    @Test
    fun `info fetcher rejects mismatched session token generation`() = runTest {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val sessionClient = mockk<TypetypeTokenYoutubeSessionClient>()
        val info = info(listOf(format(isAudio = true), format(isAudio = false)))
        val mismatched = token(visitorData = "other-visitor")
        val fallback = token()
        coEvery { sessionClient.fetchPlaybackSession("video") } returns tokenSession(info, mismatched)
        every { tokenClient.fetch("video", forceRefresh = false, refreshVideo = false) } returns fallback
        val fetcher = SabrInfoFetcher(tokenClient, sessionClient)

        val result = fetcher.fetchInfo("video")

        assertEquals(info.formats, result?.info?.formats)
        assertSame(fallback, result?.initialToken)
        verify(exactly = 1) { tokenClient.fetch("video", forceRefresh = false, refreshVideo = false) }
    }

    @Test
    fun `info fetcher keeps refreshed player context and metadata together`() = runTest {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val initial = token(visitorData = "old-visitor")
        val refreshed = token(visitorData = "fresh-visitor")
        val info = info(listOf(format(isAudio = true), format(isAudio = false)), "fresh-visitor")
        every { tokenClient.fetch("video", forceRefresh = false, refreshVideo = false) } returns initial
        every { tokenClient.fetch("video", forceRefresh = true, refreshVideo = false) } returns refreshed
        val probe = SabrPlayerInfoProbe { _, profile, token ->
            if (token === initial) {
                throw dev.typetype.server.sabr.SabrProtocolException(
                    "Player response has no streamingData for $profile",
                )
            }
            info
        }
        val fetcher = SabrInfoFetcher(tokenClient, playerInfoProbe = probe)

        val result = fetcher.fetchInfo("video")

        assertSame(info, result?.info)
        assertSame(refreshed, result?.initialToken)
        verify(exactly = 1) { tokenClient.fetch("video", forceRefresh = true, refreshVideo = false) }
    }

    private fun preparedInfo(formats: List<YoutubeSabrFormat>): SabrPreparedInfo {
        return SabrPreparedInfo(info(formats), token())
    }

    private fun info(
        formats: List<YoutubeSabrFormat>,
        visitorData: String = "visitor-data",
    ): YoutubeSabrInfo {
        val info = mockk<YoutubeSabrInfo>()
        every { info.formats } returns formats
        every { info.visitorData } returns visitorData
        every { info.profile } returns YoutubeSabrClientProfile.MWEB
        every { info.videoId } returns "video"
        every { info.cpn } returns "cpn"
        every { info.clientVersion } returns "2.20260718.00.00"
        every { info.serverAbrStreamingUrl } returns "https://example.com/sabr"
        every { info.videoPlaybackUstreamerConfig } returns "config"
        return info
    }

    private fun tokenSession(info: YoutubeSabrInfo, token: SabrTokenBundle?): TokenYoutubeSession = TokenYoutubeSession(
        info = info,
        token = token,
        title = "",
        author = "",
        channelId = "",
        channelAvatarUrl = "",
        description = "",
        durationMs = 0L,
        viewCount = 0L,
        thumbnailUrl = "",
        tags = emptyList(),
        isLive = false,
        isLiveContent = false,
    )

    private fun format(isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        every { format.itag } returns if (isAudio) 140 else 137
        every { format.audioTrackId } returns null
        every { format.xtags } returns null
        return format
    }

    private fun token(visitorData: String = "visitor-data"): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "visitor",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = visitorData,
        videoBoundPoToken = "video",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
