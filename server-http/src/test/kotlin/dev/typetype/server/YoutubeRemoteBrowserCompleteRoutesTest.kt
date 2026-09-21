package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeSessionsTable
import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.routes.youtubeRemoteBrowserRoutes
import dev.typetype.server.routes.youtubeSessionRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.YoutubeRemoteBrowserConfig
import dev.typetype.server.services.YoutubeRemoteBrowserService
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionPairingStore
import dev.typetype.server.services.YoutubeSessionService
import dev.typetype.server.services.YoutubeSessionStore
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
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class YoutubeRemoteBrowserCompleteRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val adminSettings = AdminSettingsService()
    private val json = Json { ignoreUnknownKeys = true }
    private val youtubeSessionService = YoutubeSessionService(
        YoutubeSessionCrypto.fromSecret("test-youtube-session-key-32-bytes"),
        YoutubeSessionPairingStore(codeGenerator = { "ABC12345" }),
        YoutubeSessionStore(),
    )

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `internal callback stores encrypted youtube session`() = withApp {
        adminSettings.upsert(AdminSettingsItem(youtubeRemoteLoginEnabled = true))
        val root = json.parseToJsonElement(start().bodyAsText()).jsonObject
        val sessionId = root["sessionId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val complete = client.post("/internal/youtube-remote-login/callback") {
            header("X-Internal-Token", "internal-token")
            contentType(ContentType.Application.Json)
            setBody(completeBody(sessionId))
        }
        assertEquals(HttpStatusCode.NoContent, complete.status)
        val status = client.get("/youtube-session/status") { auth() }.bodyAsText()
        assertTrue(status.contains("\"status\":\"connected\""))
        assertCredentialsAreEncrypted()
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val config = YoutubeRemoteBrowserConfig("http://token", "http://server", "internal-token", 480_000, 1, 512 * 1024, 4096, 2)
        val remoteService = YoutubeRemoteBrowserService(config, adminSettings, youtubeSessionService, FakeRemoteBrowserClient())
        application {
            install(ContentNegotiation) { json(json) }
            install(WebSockets)
            routing {
                youtubeRemoteBrowserRoutes(remoteService, auth)
                youtubeSessionRoutes(youtubeSessionService, auth)
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.start() = client.post("/youtube-session/browser/start") {
        auth()
        contentType(ContentType.Application.Json)
        setBody("""{}""")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
    }

    private fun completeBody(sessionId: String): String =
        """{"sessionId":"$sessionId","tokenSessionId":"token-session","status":"completed","cookies":"SID=secret-cookie; SAPISID=secret-sapisid","poToken":"secret-pot-value","authUser":2,"capturedAt":123}"""

    private suspend fun assertCredentialsAreEncrypted() {
        val encrypted = DatabaseFactory.query {
            YoutubeSessionsTable.selectAll().where { YoutubeSessionsTable.userId eq TEST_USER_ID }.single()
                .let {
                    Triple(
                        it[YoutubeSessionsTable.encryptedCookies],
                        it[YoutubeSessionsTable.encryptedPoToken],
                        it[YoutubeSessionsTable.authUser],
                    )
                }
        }
        assertFalse(encrypted.first.contains("secret-cookie"))
        assertFalse(encrypted.second.contains("secret-pot"))
        assertEquals(2, encrypted.third)
    }
}
