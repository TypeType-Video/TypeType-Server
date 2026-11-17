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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsWatchedRoutesTest {
    private val service = SettingsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    private fun withSettings(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { settingsRoutes(service, auth) }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.save(body: String): String {
        client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }
        return client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
    }

    @Test
    fun `watched and mini player settings keep the current behaviour by default`() = withSettings {
        val body = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"hideWatchedSubscriptionVideos\":false"))
        assertTrue(body.contains("\"watchedThresholdPercent\":0"))
        assertTrue(body.contains("\"miniPlayerEnabled\":true"))
    }

    @Test
    fun `watched and mini player settings are saved`() = withSettings {
        val body = save("""{"hideWatchedSubscriptionVideos":true,"watchedThresholdPercent":80,"miniPlayerEnabled":false}""")
        assertTrue(body.contains("\"hideWatchedSubscriptionVideos\":true"))
        assertTrue(body.contains("\"watchedThresholdPercent\":80"))
        assertTrue(body.contains("\"miniPlayerEnabled\":false"))
    }

    @Test
    fun `out of range watched thresholds fall back to the default rule`() = withSettings {
        assertTrue(save("""{"watchedThresholdPercent":30}""").contains("\"watchedThresholdPercent\":0"))
        assertTrue(save("""{"watchedThresholdPercent":101}""").contains("\"watchedThresholdPercent\":0"))
        assertTrue(save("""{"watchedThresholdPercent":100}""").contains("\"watchedThresholdPercent\":100"))
    }
}
