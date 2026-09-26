package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamRoutesTest {

    private val streamService: StreamService = mockk()

    @Test
    fun `GET streams without url returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        val response = client.get("/streams/youtube/sabr")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET streams with valid url returns 200 on Success`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(sabrResponse())
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("public, max-age=21600, stale-while-revalidate=3600", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `GET live YouTube streams exposes HLS only`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            sabrResponse().copy(isLive = true, hlsUrl = "/streams/hls-manifest?token=live"),
        )
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=live")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertTrue(body.contains("\"hlsUrl\":\"/streams/hls-manifest?token=live\""))
        assertTrue(body.contains("\"dashMpdUrl\":\"\""))
        assertTrue(body.contains("\"videoStreams\":[]"))
        assertTrue(body.contains("\"videoOnlyStreams\":[]"))
        assertTrue(body.contains("\"audioStreams\":[]"))
        assertFalse(body.contains("\"deliveryMethod\":\"sabr\""))
    }

    @Test
    fun `GET live YouTube streams fails when HLS is unavailable instead of using SABR`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(sabrResponse().copy(isLive = true, hlsUrl = ""))
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=live")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"no_playable_streams\""))
    }

    @Test
    fun `GET scheduled live events return a typed conflict`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Failure(
                "This live event will begin in 20 minutes.",
                "live_not_started",
                ExtractionFailureKind.LiveEventNotStarted,
            )
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=bad")
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"live_not_started\""))
    }

    @Test
    fun `GET scheduled premieres return a typed conflict`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Failure(
                "Premieres in 200 days",
                "scheduled_premiere",
                ExtractionFailureKind.ScheduledPremiere,
            )
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=premiere")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"scheduled_premiere\""))
    }

    @Test
    fun `GET unavailable YouTube content returns not found`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Failure(
                "This video is not available",
                "content_unavailable",
                ExtractionFailureKind.ContentUnavailable,
            )
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=missing")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"content_unavailable\""))
    }

    @Test
    fun `GET YouTube extraction failures return gateway errors`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Failure("Temporary provider error", "upstream_failure")
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=bad")
        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"upstream_failure\""))
    }

    @Test
    fun `GET provider blocks return service unavailable`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Failure(
                "The provider is blocking requests",
                "provider_access_blocked",
                ExtractionFailureKind.ProviderAccessBlocked,
            )
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=blocked")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"provider_access_blocked\""))
    }

    @Test
    fun `GET stream failure preserves non-YouTube status behavior`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Failure("Provider failure", "upstream_failure")
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }

        val response = client.get("/streams/niconico?url=https://www.nicovideo.jp/watch/sm9")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET streams returns 400 on BadRequest`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.BadRequest("This video is a paid video", "paid_content")
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=paid")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"paid_content\""))
    }

    @Test
    fun `GET restricted YouTube streams asks guests to connect YouTube`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.BadRequest("Sign in to confirm your age", "age_restricted")
        application {
            install(ContentNegotiation) { json() }
            routing {
                streamRoutes(
                    streamService = streamService,
                    youtubeSessionSabrStreamInfo = { _, _ -> null },
                )
            }
        }

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=restricted")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"youtube_session_required\""))
    }

    @Test
    fun `GET members-only metadata asks guests to connect YouTube`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(sabrResponse().copy(requiresMembership = true))
        application {
            install(ContentNegotiation) { json() }
            routing {
                streamRoutes(
                    streamService = streamService,
                    youtubeSessionSabrStreamInfo = { _, _ -> null },
                )
            }
        }

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=members")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"youtube_session_required\""))
    }

    @Test
    fun `GET sabr streams returns 422 when final response has no playable source`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse())
        application {
            install(ContentNegotiation) { json() }
            routing {
                streamRoutes(streamService) { _, data ->
                    data.copy(
                        videoStreams = emptyList(),
                        videoOnlyStreams = emptyList(),
                        audioStreams = emptyList(),
                        hlsUrl = "",
                        dashMpdUrl = "",
                    )
                }
            }
        }

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(body.contains("\"code\":\"no_playable_streams\""))
    }

    private fun sabrResponse() = testStreamResponse(
        videoOnlyStreams = listOf(testVideoStream().copy(deliveryMethod = "sabr")),
        audioStreams = listOf(testAudioStream(deliveryMethod = "sabr")),
    )
}
