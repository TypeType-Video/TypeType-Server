package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.routes.seekbarIconFileRoutes
import dev.typetype.server.routes.seekbarIconRoutes
import dev.typetype.server.routes.settingsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SeekbarIconService
import dev.typetype.server.services.SettingsService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SeekbarIconRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val iconService = SeekbarIconService()
    private val settingsService = SettingsService()
    private val gif = "GIF89a-seekbar-icon".encodeToByteArray()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        transaction {
            UsersTable.insert {
                it[id] = TEST_USER_ID
                it[email] = "seekbar@test.local"
                it[passwordHash] = "hash"
                it[name] = "Seekbar"
                it[role] = "user"
                it[createdAt] = 0L
                it[updatedAt] = 0L
            }
        }
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing {
                seekbarIconRoutes(iconService, auth)
                seekbarIconFileRoutes(iconService)
                settingsRoutes(settingsService, auth)
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.upload(bytes: ByteArray) = client.put("/profile/seekbar-icon") {
        headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        headers.append(HttpHeaders.ContentType, "image/gif")
        setBody(bytes)
    }

    private suspend fun ApplicationTestBuilder.settings() = Json.parseToJsonElement(
        client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText(),
    ).jsonObject

    @Test
    fun `new accounts use the TypeType icon`() = withApp {
        val settings = settings()
        assertEquals("typetype", settings["seekbarIcon"]?.jsonPrimitive?.content)
        assertEquals("", settings["seekbarIconUrl"]?.jsonPrimitive?.content)
    }

    @Test
    fun `uploading an icon selects it and serves it`() = withApp {
        val response = upload(gif)
        assertEquals(HttpStatusCode.OK, response.status)
        val url = Json.parseToJsonElement(response.bodyAsText()).jsonObject["seekbarIconUrl"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(url.startsWith("/seekbar-icon/$TEST_USER_ID/"))

        val settings = settings()
        assertEquals("custom", settings["seekbarIcon"]?.jsonPrimitive?.content)
        assertEquals(url, settings["seekbarIconUrl"]?.jsonPrimitive?.content)

        val file = client.get(url)
        assertEquals(HttpStatusCode.OK, file.status)
        assertEquals(ContentType.Image.GIF, file.headers[HttpHeaders.ContentType]?.let(ContentType::parse))
        assertArrayEquals(gif, file.bodyAsBytes())
    }

    @Test
    fun `unknown formats and oversized icons are refused`() = withApp {
        assertEquals(HttpStatusCode.UnsupportedMediaType, upload("not an image".encodeToByteArray()).status)
        val oversized = gif + ByteArray(SeekbarIconService.MAX_ICON_BYTES)
        assertEquals(HttpStatusCode.PayloadTooLarge, upload(oversized).status)
        assertEquals("typetype", settings()["seekbarIcon"]?.jsonPrimitive?.content)
    }

    @Test
    fun `deleting the icon goes back to the TypeType icon`() = withApp {
        val url = Json.parseToJsonElement(upload(gif).bodyAsText()).jsonObject["seekbarIconUrl"]?.jsonPrimitive?.content.orEmpty()
        val response = client.delete("/profile/seekbar-icon") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.NoContent, response.status)

        val settings = settings()
        assertEquals("typetype", settings["seekbarIcon"]?.jsonPrimitive?.content)
        assertEquals("", settings["seekbarIconUrl"]?.jsonPrimitive?.content)
        assertEquals(HttpStatusCode.NotFound, client.get(url).status)
    }

    @Test
    fun `settings writes cannot change the icon url and unknown icons fall back`() = withApp {
        val url = Json.parseToJsonElement(upload(gif).bodyAsText()).jsonObject["seekbarIconUrl"]?.jsonPrimitive?.content.orEmpty()
        val response = client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"seekbarIcon":"rocket","seekbarIconUrl":"https://example.com/x.gif"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val saved = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("typetype", saved["seekbarIcon"]?.jsonPrimitive?.content)
        assertEquals(url, saved["seekbarIconUrl"]?.jsonPrimitive?.content)
        assertEquals(url, settings()["seekbarIconUrl"]?.jsonPrimitive?.content)
        runBlocking { assertEquals(url.substringAfterLast('/'), iconService.get(TEST_USER_ID, url.substringAfterLast('/'))?.version) }
    }
}
