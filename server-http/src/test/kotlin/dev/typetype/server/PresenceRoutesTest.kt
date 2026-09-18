package dev.typetype.server

import dev.typetype.server.routes.adminSessionRoutes
import dev.typetype.server.routes.presenceRoutes
import dev.typetype.server.routes.sessionActivityRoutes
import dev.typetype.server.services.ActiveSessionService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PresenceKeyService
import dev.typetype.server.services.PresenceService
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PresenceRoutesTest {
    private val presenceKeys = PresenceKeyService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `presence key can read now playing and is revoked independently`() = runBlocking {
        insertActiveSessionUser()
        val presenceService = PresenceService(hasActiveKey = presenceKeys::hasActiveKey)
        withRoutes(presenceService) {
            val created = client.post("/presence/keys") {
                bearerAuth("test-jwt")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Discord RPC"}""")
            }
            assertEquals(HttpStatusCode.Created, created.status)
            val createdBody = Json.parseToJsonElement(created.bodyAsText()).jsonObject
            val token = createdBody["token"]?.jsonPrimitive?.contentOrNull ?: error("missing token")
            val keyId = createdBody["key"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            assertTrue(token.startsWith("ttp1_"))

            client.post("/sessions/playback/start") {
                bearerAuth("test-jwt")
                contentType(ContentType.Application.Json)
                setBody(playbackBody())
            }.let { assertEquals(HttpStatusCode.NoContent, it.status) }

            val presence = client.get("/presence/now-playing") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, presence.status)
            val presenceBody = Json.parseToJsonElement(presence.bodyAsText()).jsonObject
            assertEquals(true, presenceBody["active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())
            assertEquals("Video", presenceBody["nowPlaying"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull)
            assertEquals("Channel", presenceBody["nowPlaying"]?.jsonObject?.get("channelName")?.jsonPrimitive?.contentOrNull)

            val unauthorized = client.get("/presence/now-playing") { bearerAuth("test-jwt") }
            assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

            val listed = Json.parseToJsonElement(
                client.get("/presence/keys") { bearerAuth("test-jwt") }.bodyAsText(),
            )
            assertTrue(listed.toString().contains(""""id":"$keyId""""))
            assertFalse(listed.toString().contains(""""token":"$token""""))

            val revoked = client.delete("/presence/keys/$keyId") { bearerAuth("test-jwt") }
            assertEquals(HttpStatusCode.NoContent, revoked.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/presence/now-playing") { bearerAuth(token) }.status)
        }
    }

    @Test
    fun `presence works without admin active session tracking`() = runBlocking {
        insertActiveSessionUser()
        val presenceService = PresenceService(hasActiveKey = presenceKeys::hasActiveKey)
        withRoutes(presenceService) {
            val created = client.post("/presence/keys") {
                bearerAuth("test-jwt")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Local monitor"}""")
            }
            val token = Json.parseToJsonElement(created.bodyAsText()).jsonObject["token"]
                ?.jsonPrimitive?.contentOrNull ?: error("missing token")
            val adminSessions = client.get("/admin/sessions") { bearerAuth("test-jwt") }
            assertEquals("[]", adminSessions.bodyAsText())

            client.post("/sessions/playback/start") {
                bearerAuth("test-jwt")
                contentType(ContentType.Application.Json)
                setBody(playbackBody())
            }
            val presence = Json.parseToJsonElement(
                client.get("/presence/now-playing") { bearerAuth(token) }.bodyAsText(),
            ).jsonObject
            assertEquals(true, presence["active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())
        }
    }

    @Test
    fun `stale presence reports inactive`() = runBlocking {
        var now = 1_000L
        insertActiveSessionUser()
        val keyService = PresenceKeyService { now }
        val presenceService = PresenceService({ now }, keyService::hasActiveKey)
        withRoutes(presenceService, keyService) {
            val token = Json.parseToJsonElement(
                client.post("/presence/keys") {
                    bearerAuth("test-jwt")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Clock"}""")
                }.bodyAsText(),
            ).jsonObject["token"]?.jsonPrimitive?.contentOrNull ?: error("missing token")
            client.post("/sessions/playback/start") {
                bearerAuth("test-jwt")
                contentType(ContentType.Application.Json)
                setBody(playbackBody())
            }
            now += PresenceService.ACTIVITY_TTL_MS + 1
            val presence = Json.parseToJsonElement(
                client.get("/presence/now-playing") { bearerAuth(token) }.bodyAsText(),
            ).jsonObject
            assertEquals(false, presence["active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())
        }
    }

    private fun withRoutes(
        presenceService: PresenceService,
        keyService: PresenceKeyService = presenceKeys,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ): Unit = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                val auth = AuthService.fixed(TEST_USER_ID)
                adminSessionRoutes(auth, ActiveSessionService(AdminSettingsService()))
                presenceRoutes(auth, keyService, presenceService)
                sessionActivityRoutes(auth, ActiveSessionService(AdminSettingsService()), presenceService)
            }
        }
        block()
    }

    private fun playbackBody(): String = """
        {"clientName":"web","deviceId":"device-1","videoUrl":"https://example.test/watch?v=1",
         "title":"Video","channelName":"Channel","positionMs":1000,"durationMs":60000,"paused":false}
    """.trimIndent()
}
