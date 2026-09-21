package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.routes.adminManagedAccessRoutes
import dev.typetype.server.services.AdminManagedAccessService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.UserAdminService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val MANAGED_A_ID = "managed-a"
private const val MANAGED_B_ID = "managed-b"
private const val UNMANAGED_ID = "unmanaged-user"

class AdminManagedAccessRoutesTest {
    private val users = UserAdminService()
    private val settings = SettingsService()
    private val managed = AdminManagedAccessService()
    private val adminAuth = AuthService.fixed(TEST_USER_ID)
    private val userAuth = AuthService.fixed(UNMANAGED_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() { TestDatabase.setup() } }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        insertUser(TEST_USER_ID, "admin", "admin@test.local")
        insertUser(MANAGED_A_ID, "user", "a@test.local")
        insertUser(MANAGED_B_ID, "user", "b@test.local")
        insertUser(UNMANAGED_ID, "user", "z@test.local")
    }

    @Test
    fun `managed access lists only admin managed users`() = withApp(adminAuth) {
        settings.upsert(UNMANAGED_ID, SettingsItem(accessMode = "allow_list"))
        users.setAccessMode(MANAGED_A_ID, "allow_list")
        Thread.sleep(2)
        users.setAccessMode(MANAGED_B_ID, "unrestricted")
        val body = managedAccessBody()
        assertTrue(body.contains("\"id\":\"$MANAGED_B_ID\""))
        assertTrue(body.contains("\"accessMode\":\"unrestricted\""))
        assertTrue(body.contains("\"id\":\"$MANAGED_A_ID\""))
        assertTrue(body.contains("\"accessMode\":\"allow_list\""))
        assertTrue(body.contains("\"adminManagedAccessMode\":true"))
        assertFalse(body.contains(UNMANAGED_ID))
        assertTrue(body.indexOf(MANAGED_B_ID) < body.indexOf(MANAGED_A_ID))
    }

    @Test
    fun `managed access paginates with nextpage`() = withApp(adminAuth) {
        users.setAccessMode(MANAGED_A_ID, "allow_list")
        Thread.sleep(2)
        users.setAccessMode(MANAGED_B_ID, "allow_list")
        val first = managedAccessBody("?limit=1")
        assertTrue(first.contains("\"nextpage\":\"1\""))
        assertTrue(first.contains(MANAGED_B_ID))
        assertFalse(first.contains(MANAGED_A_ID))
        val second = managedAccessBody("?limit=1&page=1")
        assertTrue(second.contains("\"nextpage\":null"))
        assertTrue(second.contains(MANAGED_A_ID))
    }

    @Test
    fun `managed access rejects non admin`() = withApp(userAuth) {
        val response = client.get("/admin/users/managed-access") { authHeader() }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    private fun withApp(auth: AuthService, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { install(ContentNegotiation) { json() }; routing { adminManagedAccessRoutes(auth, managed) } }
        block()
    }

    private suspend fun ApplicationTestBuilder.managedAccessBody(query: String = ""): String =
        client.get("/admin/users/managed-access$query") { authHeader() }.bodyAsText()

    private fun io.ktor.client.request.HttpRequestBuilder.authHeader() {
        headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
    }
}

private fun insertUser(userId: String, role: String, email: String) = transaction {
    UsersTable.insert { it[id] = userId; it[UsersTable.role] = role; it[UsersTable.email] = email; it[passwordHash] = "hash"; it[name] = userId; it[createdAt] = 10L; it[updatedAt] = 10L }
}
