package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeSessionsTable
import dev.typetype.server.models.YoutubeSessionPairingResponse
import dev.typetype.server.routes.youtubeSessionRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionPairingStore
import dev.typetype.server.services.YoutubeSessionService
import dev.typetype.server.services.YoutubeSessionStore
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class YoutubeSessionRoutesTest {
    private var now = 1_000L
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val service = YoutubeSessionService(
        YoutubeSessionCrypto.fromSecret("test-youtube-session-key-32-bytes"),
        YoutubeSessionPairingStore(nowMillis = { now }, codeGenerator = { "ABC12345" }),
        YoutubeSessionStore(nowMillis = { now }),
    )
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }
    @BeforeEach
    fun clean() {
        now = 1_000L
        TestDatabase.truncateAll()
    }
    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(json) }
            routing { youtubeSessionRoutes(service, auth) }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.pairingCode(): String =
        json.decodeFromString<YoutubeSessionPairingResponse>(client.post("/youtube-session/pairing") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()).code

    private suspend fun ApplicationTestBuilder.completeSession(code: String, authUser: Int = 2) = client.post("/youtube-session/complete") {
        headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            """{"code":"$code","cookies":"SID=secret-cookie; SAPISID=secret-sapisid","poToken":"secret-pot-value","authUser":$authUser}""",
        )
    }

    @Test
    fun `pairing requires auth`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.post("/youtube-session/pairing").status)
    }

    @Test
    fun `complete stores encrypted session and status hides secrets`() = withApp {
        assertEquals(HttpStatusCode.NoContent, completeSession(pairingCode()).status)
        val status = client.get("/youtube-session/status") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(status.contains("\"status\":\"connected\""))
        assertFalse(status.contains("secret-cookie"))
        assertFalse(status.contains("secret-pot"))
        assertCredentialsAreEncrypted()
    }

    @Test
    fun `delete disconnects session`() = withApp {
        completeSession(pairingCode())
        assertEquals(HttpStatusCode.NoContent, client.delete("/youtube-session") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.status)
        val body = client.get("/youtube-session/status") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(body.contains("\"status\":\"disconnected\""))
    }

    @Test
    fun `complete rejects expired pairing code`() = withApp {
        val code = pairingCode()
        now += 6 * 60 * 1000L
        assertEquals(HttpStatusCode.Gone, completeSession(code).status)
    }

    @Test
    fun `complete rejects an invalid Google account index`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, completeSession(pairingCode(), authUser = 100).status)
    }

    private suspend fun assertCredentialsAreEncrypted() {
        val encrypted = DatabaseFactory.query {
            val row = YoutubeSessionsTable.selectAll().where { YoutubeSessionsTable.userId eq TEST_USER_ID }.single()
            Triple(
                row[YoutubeSessionsTable.encryptedCookies],
                row[YoutubeSessionsTable.encryptedPoToken],
                row[YoutubeSessionsTable.authUser],
            )
        }
        assertFalse(encrypted.first.contains("secret-cookie"))
        assertFalse(encrypted.second.contains("secret-pot"))
        assertEquals(2, encrypted.third)
    }
}
