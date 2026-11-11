package dev.typetype.server

import dev.typetype.server.routes.settingsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SettingsService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsPrivacyControlsRoutesTest {
    private val service = SettingsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() {
            TestDatabase.setup()
        }
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { settingsRoutes(service, auth) }
        }
        block()
    }

    @Test
    fun `GET settings returns SponsorBlock and privacy defaults`() = withApp {
        val body = client.get("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()

        assertContainsAll(
            body,
            listOf(
                "\"sponsorBlockMode\":\"auto_skip\"",
                "\"hideHomeRecommendations\":false",
                "\"hideRelatedVideos\":false",
                "\"hideEndScreens\":false",
                "\"hideComments\":false",
                "\"hideShorts\":false",
                "\"hideSubscriptionLiveStreams\":false",
                "\"hideMembersOnlyContent\":false",
            ),
        )
    }

    @Test
    fun `PUT settings persists SponsorBlock and privacy controls`() = withApp {
        val put = client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(settingsBody())
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val body = client.get("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertContainsAll(
            body,
            listOf(
                "\"sponsorBlockMode\":\"mark_only\"",
                "\"hideHomeRecommendations\":true",
                "\"hideRelatedVideos\":true",
                "\"hideEndScreens\":true",
                "\"hideComments\":true",
                "\"hideShorts\":true",
                "\"hideSubscriptionLiveStreams\":true",
                "\"hideMembersOnlyContent\":true",
            ),
        )
    }

    @Test
    fun `PUT settings rejects invalid SponsorBlock mode`() = withApp {
        val response = client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(settingsBody(sponsorBlockMode = "skip_everything"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private fun assertContainsAll(body: String, values: List<String>) =
        values.forEach { assertTrue(body.contains(it)) }

    private fun settingsBody(sponsorBlockMode: String = "mark_only"): String = """
        {"defaultService":0,"defaultQuality":"1080p","autoplay":true,"volume":1.0,"muted":false,"sponsorBlockMode":"$sponsorBlockMode","hideHomeRecommendations":true,"hideRelatedVideos":true,"hideEndScreens":true,"hideComments":true,"hideShorts":true,"hideSubscriptionLiveStreams":true,"hideMembersOnlyContent":true}
    """.trimIndent()
}
