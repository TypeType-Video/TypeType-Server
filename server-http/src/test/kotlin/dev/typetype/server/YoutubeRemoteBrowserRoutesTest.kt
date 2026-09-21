package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.routes.youtubeRemoteBrowserRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.YoutubeRemoteBrowserConfig
import dev.typetype.server.services.YoutubeRemoteBrowserService
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionPairingStore
import dev.typetype.server.services.YoutubeSessionService
import dev.typetype.server.services.YoutubeSessionStore
import io.ktor.client.request.delete
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class YoutubeRemoteBrowserRoutesTest {
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
    fun `start is unavailable when internal config is missing`() = withApp(config(internalToken = null)) {
        adminSettings.upsert(AdminSettingsItem(youtubeRemoteLoginEnabled = true))
        assertEquals(HttpStatusCode.ServiceUnavailable, start().status)
    }

    @Test
    fun `start is disabled until admin enables it`() = withApp(config()) {
        assertEquals(HttpStatusCode.Forbidden, start().status)
    }

    @Test
    fun `start returns ws url and enforces one active session per user`() = withApp(config()) {
        adminSettings.upsert(AdminSettingsItem(youtubeRemoteLoginEnabled = true))
        val first = start()
        assertEquals(HttpStatusCode.Created, first.status)
        val root = json.parseToJsonElement(first.bodyAsText()).jsonObject
        assertTrue(root["wsUrl"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("?token="))
        assertEquals(HttpStatusCode.Conflict, start().status)
    }

    @Test
    fun `cancel only removes owner session`() = withApp(config()) {
        adminSettings.upsert(AdminSettingsItem(youtubeRemoteLoginEnabled = true))
        val root = json.parseToJsonElement(start().bodyAsText()).jsonObject
        val sessionId = root["sessionId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        assertEquals(HttpStatusCode.NoContent, client.delete("/youtube-session/browser/$sessionId") { auth() }.status)
    }

    private fun withApp(config: YoutubeRemoteBrowserConfig, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val remoteService = YoutubeRemoteBrowserService(config, adminSettings, youtubeSessionService, FakeRemoteBrowserClient())
        application {
            install(ContentNegotiation) { json(json) }
            install(WebSockets)
            routing {
                youtubeRemoteBrowserRoutes(remoteService, auth)
            }
        }
        block()
    }

    private fun config(internalToken: String? = "internal-token") =
        YoutubeRemoteBrowserConfig("http://token", "http://server", internalToken, 480_000, 1, 512 * 1024, 4096, 2)

    private suspend fun ApplicationTestBuilder.start() = client.post("/youtube-session/browser/start") {
        auth()
        contentType(ContentType.Application.Json)
        setBody("""{}""")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
    }

}
