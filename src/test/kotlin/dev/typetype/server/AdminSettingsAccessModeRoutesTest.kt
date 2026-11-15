package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.routes.adminRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.UserAdminService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminSettingsAccessModeRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val users = UserAdminService()
    private val resets = PasswordResetService()
    private val settings = AdminSettingsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        transaction {
            UsersTable.insert {
                it[id] = TEST_USER_ID
                it[email] = "admin@test.local"
                it[passwordHash] = "hash"
                it[name] = "Admin"
                it[role] = "admin"
                it[createdAt] = 10L
                it[updatedAt] = 10L
            }
        }
    }

    @Test
    fun `PUT admin settings persists global allow list mode`() = withApp {
        putSettings("allow_list")
        val body = client.get("/admin/settings") { authHeader() }.bodyAsText()
        assertTrue(body.contains("\"accessMode\":\"allow_list\""))
    }

    @Test
    fun `PUT admin settings persists applyParentalControlsToAdmins and defaults to false`() = withApp {
        val initial = client.get("/admin/settings") { authHeader() }.bodyAsText()
        assertTrue(initial.contains("\"applyParentalControlsToAdmins\":false"))
        client.put("/admin/settings") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody("""{"name":"TypeType","accessMode":"allow_list","applyParentalControlsToAdmins":true}""")
        }
        val updated = client.get("/admin/settings") { authHeader() }.bodyAsText()
        assertTrue(updated.contains("\"applyParentalControlsToAdmins\":true"))
    }

    @Test
    fun `PUT admin settings normalizes unknown access mode to unrestricted`() = withApp {
        putSettings("bad")
        val body = client.get("/admin/settings") { authHeader() }.bodyAsText()
        assertTrue(body.contains("\"accessMode\":\"unrestricted\""))
    }

    @Test
    fun `admin settings responses are not cacheable`() = withApp {
        putSettings("unrestricted")
        val getResponse = client.get("/admin/settings") { authHeader() }
        assertEquals("no-store, no-cache, must-revalidate, max-age=0", getResponse.headers[HttpHeaders.CacheControl])
        val putResponse = client.put("/admin/settings") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody("""{"name":"TypeType","youtubeRemoteLoginEnabled":true}""")
        }
        assertEquals("no-store, no-cache, must-revalidate, max-age=0", putResponse.headers[HttpHeaders.CacheControl])
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { adminRoutes(auth, users, resets, settings) }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.putSettings(accessMode: String) {
        client.put("/admin/settings") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody("""{"name":"TypeType","accessMode":"$accessMode"}""")
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeader() {
        headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
    }
}
