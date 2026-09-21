package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.routes.adminRoutes
import dev.typetype.server.routes.adminUserAccessModeRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.UserAdminService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
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
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val FAMILY_USER_ID = "family-user"
private const val MODERATOR_USER_ID = "moderator-user"

class AdminUserAccessModeRoutesTest {
    private val users = UserAdminService()
    private val settings = SettingsService()
    private val resets = PasswordResetService()
    private val adminSettings = AdminSettingsService()
    private val adminAuth = AuthService.fixed(TEST_USER_ID)
    private val moderatorAuth = AuthService.fixed(MODERATOR_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() { TestDatabase.setup() } }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        insertUser(TEST_USER_ID, "admin")
        insertUser(FAMILY_USER_ID, "user")
        insertUser(MODERATOR_USER_ID, "moderator")
    }

    @Test
    fun `GET admin users includes access mode`() = withApp(adminAuth) {
        settings.upsert(FAMILY_USER_ID, SettingsItem(accessMode = "allow_list"))
        val body = client.get("/admin/users") { authHeader() }.bodyAsText()
        assertTrue(body.contains("\"id\":\"$FAMILY_USER_ID\""))
        assertTrue(body.contains("\"accessMode\":\"allow_list\""))
    }

    @Test
    fun `PUT admin user access mode updates user setting`() = withApp(adminAuth) {
        val response = putAccessMode(FAMILY_USER_ID, "allow_list")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"accessMode\":\"allow_list\""))
        assertEquals("allow_list", settings.get(FAMILY_USER_ID).accessMode)
        assertTrue(settings.getAccessModePolicy(FAMILY_USER_ID).adminManaged)
    }

    @Test
    fun `PUT admin user access mode returns 404 for missing user`() = withApp(adminAuth) {
        val response = putAccessMode("missing", "allow_list")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT admin user access mode rejects moderator`() = withApp(moderatorAuth) {
        val response = putAccessMode(FAMILY_USER_ID, "allow_list")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    private fun withApp(auth: AuthService, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                adminRoutes(auth, users, resets, adminSettings)
                adminUserAccessModeRoutes(auth, users)
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.putAccessMode(userId: String, accessMode: String) =
        client.put("/admin/users/$userId/access-mode") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody("""{"accessMode":"$accessMode"}""")
        }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeader() {
        headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
    }
}

private fun insertUser(userId: String, role: String) = transaction {
    UsersTable.insert {
        it[id] = userId
        it[email] = "$userId@test.local"
        it[passwordHash] = "hash"
        it[name] = userId
        it[UsersTable.role] = role
        it[createdAt] = 10L
        it[updatedAt] = 10L
    }
}
