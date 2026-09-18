package dev.typetype.server

import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.routes.youtubeSubtitleRoutes
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.ResolvedYouTubeSubtitle
import dev.typetype.server.services.YouTubeSubtitleCache
import dev.typetype.server.services.YouTubeSubtitleDeliveryService
import dev.typetype.server.services.YouTubeSubtitleFetchResult
import dev.typetype.server.services.YouTubeSubtitleResolution
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeSubtitleProxyRoutesTest {
    private val proxyService: ProxyService = mockk(relaxed = true)

    @Test
    fun `dedicated YouTube subtitle route returns cacheable WebVTT`() = testApplication {
        val service = subtitleService(YouTubeSubtitleFetchResult.Ready(VTT))
        application {
            install(ContentNegotiation) { json() }
            routing { youtubeSubtitleRoutes(service) }
        }

        val response = client.get("/subtitles/youtube/abcdefghijk") {
            parameter("language", "en")
            parameter("variant", "manual")
            parameter("format", "vtt")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("text/vtt") == true)
        assertEquals("public, max-age=21600, stale-while-revalidate=3600", response.headers[HttpHeaders.CacheControl])
        assertTrue(response.bodyAsText().startsWith("WEBVTT"))
    }

    @Test
    fun `live YouTube subtitle route disables response caching`() = testApplication {
        val service = subtitleService(YouTubeSubtitleFetchResult.Ready(VTT), isLive = true)
        application {
            install(ContentNegotiation) { json() }
            routing { youtubeSubtitleRoutes(service) }
        }

        val response = client.get("/subtitles/youtube/abcdefghijk") {
            parameter("language", "en")
            parameter("variant", "auto")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `legacy timed text proxy uses dedicated subtitle delivery`() = testApplication {
        val service = subtitleService(YouTubeSubtitleFetchResult.Ready(VTT))
        application {
            install(ContentNegotiation) { json() }
            routing { proxyRoutes(proxyService, service) }
        }

        val response = client.get("/proxy") {
            parameter("url", "https://www.youtube.com/api/timedtext?v=abcdefghijk&lang=en")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().startsWith("WEBVTT"))
        coVerify(exactly = 0) { proxyService.pipe(any(), any(), any()) }
    }

    @Test
    fun `YouTube subtitle throttle returns a typed request id error`() = testApplication {
        val service = subtitleService(YouTubeSubtitleFetchResult.Throttled)
        application {
            installRequestObservability()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            configureCompression()
            configureStatusPages()
            routing { youtubeSubtitleRoutes(service) }
        }

        val response = client.get("/subtitles/youtube/abcdefghijk") {
            header(REQUEST_ID_HEADER, "subtitle-request-123")
            header(HttpHeaders.AcceptEncoding, "gzip")
            parameter("language", "en")
            parameter("variant", "manual")
        }

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("subtitle-request-123", response.headers[REQUEST_ID_HEADER])
        assertEquals(null, response.headers[HttpHeaders.ContentEncoding])
        val body = response.bodyAsText()
        assertTrue(body.contains("\"code\":\"subtitle_upstream_throttled\""))
        assertTrue(body.contains("\"requestId\":\"subtitle-request-123\""))
    }

    @Test
    fun `invalid dedicated subtitle selection returns typed bad request`() = testApplication {
        val service = subtitleService(YouTubeSubtitleFetchResult.Ready(VTT))
        application {
            installRequestObservability()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            configureStatusPages()
            routing { youtubeSubtitleRoutes(service) }
        }

        val response = client.get("/subtitles/youtube/not-valid") {
            parameter("language", "en")
            parameter("variant", "manual")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"subtitle_request_invalid\""))
    }

    private fun subtitleService(
        fetchResult: YouTubeSubtitleFetchResult,
        isLive: Boolean = false,
    ) = YouTubeSubtitleDeliveryService(
        resolver = { YouTubeSubtitleResolution.Ready(ResolvedYouTubeSubtitle(TIMED_TEXT_URL, true, isLive)) },
        fetcher = { _, _ -> fetchResult },
        cache = YouTubeSubtitleCache(null),
    )

    private companion object {
        val VTT = "WEBVTT\n\n00:00.000 --> 00:01.000\nHello".encodeToByteArray()
        const val TIMED_TEXT_URL = "https://www.youtube.com/api/timedtext?v=abcdefghijk&lang=en&fmt=vtt"
    }
}
