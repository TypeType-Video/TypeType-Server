package dev.typetype.server

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.FocusAiReviewResponse
import dev.typetype.server.routes.focusRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FocusAiReviewResult
import dev.typetype.server.services.FocusService
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusAiReviewRoutesTest {
    private val focusService: FocusService = mockk()
    private val authService = AuthService.fixed(TEST_USER_ID)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `maps approved rejected unavailable cooldown and invalid ai review outcomes`() = testApplication {
        val url = "https://www.youtube.com/watch?v=vidAi001"
        coEvery { focusService.reviewWithAi(TEST_USER_ID, url, "Need for math class", "Asia/Shanghai") } returns
            FocusAiReviewResult.Approved(expiresAt = 5_000L)
        coEvery { focusService.reviewWithAi(TEST_USER_ID, url, "Just bored", "Asia/Shanghai") } returns
            FocusAiReviewResult.Rejected(cooldownMs = 3_600_000L, rejectionExpiresAt = 9_000L)
        coEvery { focusService.reviewWithAi(TEST_USER_ID, url, "Upstream error", null) } returns
            FocusAiReviewResult.Unavailable
        coEvery { focusService.reviewWithAi(TEST_USER_ID, url, "Try again", null) } returns
            FocusAiReviewResult.CooldownActive(cooldownMs = 3_600_000L, rejectionExpiresAt = 9_000L)
        coEvery { focusService.reviewWithAi(TEST_USER_ID, url, "   ", null) } returns
            FocusAiReviewResult.InvalidReason

        application {
            install(ContentNegotiation) { json() }
            routing { focusRoutes(focusService, authService) }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.post("/focus/ai-review").status)

        val approved = client.post("/focus/ai-review") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"videoUrl":"$url","reason":"Need for math class","timezone":"Asia/Shanghai"}""")
        }
        assertEquals(HttpStatusCode.OK, approved.status)
        val approvedBody = json.decodeFromString(FocusAiReviewResponse.serializer(), approved.bodyAsText())
        assertEquals("approved", approvedBody.status)
        assertEquals(5_000L, approvedBody.expiresAt)

        val rejected = client.post("/focus/ai-review") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"videoUrl":"$url","reason":"Just bored","timezone":"Asia/Shanghai"}""")
        }
        assertEquals(HttpStatusCode.OK, rejected.status)
        val rejectedBody = json.decodeFromString(FocusAiReviewResponse.serializer(), rejected.bodyAsText())
        assertEquals("rejected", rejectedBody.status)
        assertEquals(3_600_000L, rejectedBody.cooldownMs)

        val unavailable = client.post("/focus/ai-review") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"videoUrl":"$url","reason":"Upstream error"}""")
        }
        assertEquals(HttpStatusCode.OK, unavailable.status)
        assertTrue(unavailable.bodyAsText().contains("\"unavailable\""))

        val cooldown = client.post("/focus/ai-review") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"videoUrl":"$url","reason":"Try again"}""")
        }
        assertEquals(HttpStatusCode.Conflict, cooldown.status)
        assertEquals("focus_ai_rejection_cooldown", json.decodeFromString(ErrorResponse.serializer(), cooldown.bodyAsText()).code)

        val invalid = client.post("/focus/ai-review") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"videoUrl":"$url","reason":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals("focus_ai_invalid_reason", json.decodeFromString(ErrorResponse.serializer(), invalid.bodyAsText()).code)
    }
}
