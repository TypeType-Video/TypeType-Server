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
import kotlinx.serialization.json.Json
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsRoutesTest {
    private val service = SettingsService()
    private val auth = AuthService.fixed(TEST_USER_ID)
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }
    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }
    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { settingsRoutes(service, auth) }
        }
        block()
    }
    private val settingsBody = """{"defaultService":0,"defaultQuality":"1080p","autoplay":true,"volume":1.0,"muted":false}"""
    private fun assertContainsAll(body: String, values: List<String>) =
        values.forEach { assertTrue(body.contains(it)) }
    private fun assertContainsNone(body: String, values: List<String>) =
        values.forEach { assertTrue(!body.contains(it)) }

    @Test
    fun `GET settings without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/settings").status)
    }

    @Test
    fun `GET settings returns 200 with defaults when no row exists`() = withApp {
        val response = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"volume\":1.0"))
        assertTrue(body.contains("\"muted\":false"))
        assertTrue(body.contains("\"notificationPopupsEnabled\":true"))
        assertTrue(body.contains("\"defaultLandingPage\":\"home\""))
        assertTrue(body.contains("\"defaultPlaybackSpeed\":1.0"))
    }

    @Test
    fun `PUT settings returns 200 and persists values`() = withApp {
        val response = client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(settingsBody)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"volume\":1.0"))
        assertTrue(body.contains("\"muted\":false"))
    }

    @Test
    fun `GET settings returns persisted values after PUT`() = withApp {
        client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"defaultService":0,"defaultQuality":"720p","defaultLandingPage":"subscriptions","autoplay":false,"volume":0.5,"muted":true,"notificationPopupsEnabled":false}""")
        }
        val body = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"volume\":0.5"))
        assertTrue(body.contains("\"muted\":true"))
        assertTrue(body.contains("\"notificationPopupsEnabled\":false"))
        assertTrue(body.contains("\"defaultQuality\":\"720p\""))
        assertTrue(body.contains("\"defaultLandingPage\":\"subscriptions\""))
    }

    @Test
    fun `GET settings returns defaults for new fields when no row exists`() = withApp {
        val body = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertContainsAll(body, listOf("\"subtitlesEnabled\":false", "\"defaultSubtitleLanguage\":\"\"", "\"defaultAudioLanguage\":\"\"", "\"preferOriginalLanguage\":false", "\"enableHighQualityPlayback\":false"))
        assertContainsNone(body, listOf("recommendationPersonalizationEnabled", "subscriptionSyncInterval"))
    }

    @Test
    fun `PUT settings persists new fields and GET returns them`() = withApp {
        client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"defaultService":0,"defaultQuality":"1080p","autoplay":true,"volume":1.0,"muted":false,"subtitlesEnabled":true,"defaultSubtitleLanguage":"fr","defaultAudioLanguage":"fr","preferOriginalLanguage":true,"enableHighQualityPlayback":true,"subscriptionSyncInterval":60}""")
        }
        val body = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertContainsAll(body, listOf("\"subtitlesEnabled\":true", "\"defaultSubtitleLanguage\":\"fr\"", "\"defaultAudioLanguage\":\"fr\"", "\"preferOriginalLanguage\":true", "\"enableHighQualityPlayback\":true"))
        assertContainsNone(body, listOf("recommendationPersonalizationEnabled", "subscriptionSyncInterval"))
    }

    @Test
    fun `PUT settings with invalid body returns 400`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""not json""")
        }.status)
    }

    @Test
    fun `PUT settings persists and bounds default playback speed`() = withApp {
        client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"defaultPlaybackSpeed":8.0}""")
        }
        val body = client.get("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(body.contains("\"defaultPlaybackSpeed\":4.0"))
    }
}
