package dev.typetype.server

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.routes.youtubeEgressResetRoutes
import dev.typetype.server.services.YoutubeEgressResetResponse
import dev.typetype.server.services.YoutubeEgressResetService
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class YoutubeEgressResetRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reset returns 404 when unconfigured or missing valid token`() = testApplication {
        var recovered: String? = null
        val unconfigured = YoutubeEgressResetService(
            cache = FakeCacheService(),
            clearFailureMemory = { 0 },
            evictConnections = {},
            recoverTokenSession = { reason ->
                recovered = reason
                true
            },
            tokenProvider = { null },
        )
        val configured = YoutubeEgressResetService(
            cache = FakeCacheService(),
            clearFailureMemory = { 0 },
            evictConnections = {},
            recoverTokenSession = { reason ->
                recovered = reason
                true
            },
            tokenProvider = { "secret-token" },
        )
        application {
            install(ContentNegotiation) { json(json) }
            configureStatusPages()
            routing {
                youtubeEgressResetRoutes(configured)
            }
        }

        val missing = client.post("/internal/provider/youtube/reset")
        assertEquals(HttpStatusCode.NotFound, missing.status)

        val wrong = client.post("/internal/provider/youtube/reset") {
            header("X-Internal-Token", "wrong")
        }
        assertEquals(HttpStatusCode.NotFound, wrong.status)
        assertNull(recovered)

        testApplication {
            application {
                install(ContentNegotiation) { json(json) }
                routing { youtubeEgressResetRoutes(unconfigured) }
            }
            val disabled = client.post("/internal/provider/youtube/reset") {
                header("X-Internal-Token", "secret-token")
            }
            assertEquals(HttpStatusCode.NotFound, disabled.status)
        }
    }

    @Test
    fun `reset succeeds with empty body and throttles repeat calls inside cooldown`() = testApplication {
        var now = 10_000L
        val cache = FakeCacheService()
        cache.set("youtube-related:v1:dQw4w9WgXcQ:backoff", "{}", 60)
        var evicted = 0
        var recoveredReason: String? = null
        val service = YoutubeEgressResetService(
            cache = cache,
            clearFailureMemory = { 3 },
            evictConnections = { evicted += 1 },
            recoverTokenSession = { reason ->
                recoveredReason = reason
                true
            },
            tokenProvider = { "secret-token" },
            restMs = 120_000L,
            nowMs = { now },
        )
        application {
            install(ContentNegotiation) { json(json) }
            configureStatusPages()
            routing { youtubeEgressResetRoutes(service) }
        }

        val first = client.post("/internal/provider/youtube/reset") {
            header("X-Internal-Token", "secret-token")
        }
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(YoutubeEgressResetResponse(3, 1, true), json.decodeFromString<YoutubeEgressResetResponse>(first.bodyAsText()))
        assertEquals(1, evicted)
        assertEquals(YoutubeEgressResetService.DEFAULT_REASON, recoveredReason)

        now += 45_000L
        val throttled = client.post("/internal/provider/youtube/reset") {
            header("X-Internal-Token", "secret-token")
        }
        assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
        assertEquals("75", throttled.headers[HttpHeaders.RetryAfter])
        assertEquals("egress_reset_cooldown", json.decodeFromString<ErrorResponse>(throttled.bodyAsText()).code)
        assertEquals(1, evicted)
    }

    @Test
    fun `reset accepts bearer token and custom reason`() = testApplication {
        var recoveredReason: String? = null
        val service = YoutubeEgressResetService(
            cache = FakeCacheService(),
            clearFailureMemory = { 0 },
            evictConnections = {},
            recoverTokenSession = { reason ->
                recoveredReason = reason
                false
            },
            tokenProvider = { "secret-token" },
            restMs = 0L,
        )
        application {
            install(ContentNegotiation) { json(json) }
            configureStatusPages()
            routing { youtubeEgressResetRoutes(service) }
        }

        val response = client.post("/internal/provider/youtube/reset") {
            header(HttpHeaders.Authorization, "Bearer secret-token")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"egress_rotated"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(YoutubeEgressResetResponse(0, 0, false), json.decodeFromString<YoutubeEgressResetResponse>(response.bodyAsText()))
        assertEquals("egress_rotated", recoveredReason)
    }
}
