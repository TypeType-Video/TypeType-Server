package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.BilibiliRelatedService
import dev.typetype.server.services.NewPipeInitializer
import dev.typetype.server.services.PipePipeStreamService
import dev.typetype.server.services.TypetypeTokenSabrPoTokenProvider
import dev.typetype.server.services.TypetypeTokenSabrTokenClient
import dev.typetype.server.services.TypetypeTokenYoutubeSessionClient
import dev.typetype.server.services.YouTubeSubtitleService
import dev.typetype.server.services.YoutubePlayerClient
import dev.typetype.server.services.YoutubePlayerClientStreamService
import dev.typetype.server.services.YoutubeSessionCookieNormalizer
import dev.typetype.server.services.YoutubeSessionCredentials
import dev.typetype.server.services.YoutubeSessionTokenScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.YoutubeSabrSession
import java.nio.file.Files
import java.nio.file.Path

@Tag("network")
@EnabledIfEnvironmentVariable(named = "YOUTUBE_AUTH_COOKIES_FILE", matches = ".+")
class YoutubeAuthenticatedExtractionProbeTest {
    private val tokenServiceUrl = System.getenv("YOUTUBE_TOKEN_SERVICE_URL") ?: "http://127.0.0.1:8081"
    private val videoId = System.getenv("YOUTUBE_PROBE_VIDEO_ID") ?: "dQw4w9WgXcQ"
    private val expectSessionRejection = System.getenv("YOUTUBE_EXPECT_SESSION_REJECTION").toBoolean()
    private val videoUrl = "https://www.youtube.com/watch?v=$videoId"
    private val localization = Localization("en", "US")
    private val contentCountry = ContentCountry("US")

    @Test
    fun `authenticated classic extraction receives a session bound player token`() = runBlocking {
        NewPipeInitializer.init(tokenServiceUrl)
        val cookies = readCookies()
        val credentials = YoutubeSessionCredentials("probe", "probe", cookies, "probe-token", authUser = 1)
        val pipePipe = PipePipeStreamService(
            ProbeCache,
            YouTubeSubtitleService(OkHttpClient(), tokenServiceUrl),
            BilibiliRelatedService(),
        )
        val service = YoutubePlayerClientStreamService(
            pipePipe,
            YoutubePlayerClient.MWEB,
        )

        val result = YoutubeSessionTokenScope.withCredentials(credentials) {
            val token = NewPipe.getYoutubeSessionPoTokenProvider()?.getSessionPoToken(
                "TV",
                "1.0",
                "test-user-agent",
                localization,
                contentCountry,
                true,
            )
            assertTrue(token?.visitorData?.isNotBlank() == true)
            assertTrue(token?.poToken?.isNotBlank() == true)
            service.getStreamInfo(videoUrl)
        }

        if (expectSessionRejection) {
            assertTrue(result is ExtractionResult.Failure, result.toString())
            assertEquals(
                ExtractionFailureKind.YoutubeSessionRejected,
                (result as ExtractionResult.Failure).kind,
            )
            return@runBlocking
        }
        assertTrue(result is ExtractionResult.Success, result.toString())
        val stream = (result as ExtractionResult.Success).data
        assertTrue(stream.videoStreams.isNotEmpty() || stream.videoOnlyStreams.isNotEmpty() || stream.hlsUrl.isNotBlank())
        assertFalse(ServiceList.YouTube.hasTokens())
    }

    @Test
    fun `anonymous SABR session remains independent from connected credentials`() = runBlocking {
        NewPipeInitializer.init(tokenServiceUrl)
        val tokenClient = TypetypeTokenSabrTokenClient(tokenServiceUrl)
        val playback = YoutubeSessionTokenScope.withoutCredentials {
            assertFalse(ServiceList.YouTube.hasTokens())
            TypetypeTokenYoutubeSessionClient(tokenServiceUrl).fetchPlaybackSession(videoId)
        } ?: error("Anonymous SABR bootstrap failed")
        assertFalse(ServiceList.YouTube.hasTokens())
        val token = playback.token ?: error("Anonymous SABR token is missing")
        val audio = playback.info.findBestAudioFormat() ?: error("SABR audio format is missing")
        val video = playback.info.findLowestVideoFormat() ?: error("SABR video format is missing")
        val session = YoutubeSabrSession(
            playback.info,
            audio,
            video,
            TypetypeTokenSabrPoTokenProvider(tokenClient, token),
        )

        val segments = session.pumpOnce(localization)

        assertTrue(segments.isNotEmpty())
        assertFalse(ServiceList.YouTube.hasTokens())
    }

    private fun readCookies(): String {
        val raw = Files.readString(Path.of(System.getenv("YOUTUBE_AUTH_COOKIES_FILE")))
        return YoutubeSessionCookieNormalizer.normalize(raw) ?: error("Cookie export is invalid")
    }
}

private object ProbeCache : CacheService {
    override suspend fun get(key: String): String? = null
    override suspend fun set(key: String, value: String, ttlSeconds: Long): Unit = Unit
    override suspend fun delete(key: String): Unit = Unit
}
