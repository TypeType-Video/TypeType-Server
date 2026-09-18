package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.RssFeedRequest
import dev.typetype.server.routes.adminRssRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.RssFeedManagementService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.delete
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
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminRssRoutesTest {
    private val settings = AdminSettingsService()
    private val service = RssFeedManagementService(settings, SubscriptionsService())
    private val auth = AuthService.fixed(ADMIN_ID)

    companion object {
        private const val ADMIN_ID = "rss-admin"
        private const val USER_ID = "rss-user"

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        insertUser(ADMIN_ID, "admin")
        insertUser(USER_ID, "user")
    }

    @Test
    fun `admin can inspect and revoke a feed without receiving its secret`() = withApp {
        enableRss()
        val created = service.create(USER_ID, RssFeedRequest(name = "Private feed"))

        val listed = client.get("/admin/rss/feeds") { authorize() }
        assertEquals(HttpStatusCode.OK, listed.status)
        val body = listed.bodyAsText()
        assertTrue(body.contains("user@test.local"))
        assertTrue(body.contains(created.feed.id))
        assertFalse(body.contains("feedUrl"))
        assertFalse(body.contains("token"))

        val deleted = client.delete("/admin/rss/feeds/${created.feed.id}") { authorize() }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(0L, service.adminList(1, 20).total)
    }

    @Test
    fun `admin account policy is retained and unknown accounts return 404`() = withApp {
        enableRss()
        service.create(USER_ID, RssFeedRequest(name = "Private feed"))

        val disabled = client.put("/admin/rss/users/$USER_ID/enabled") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody("""{"enabled":false}""")
        }
        assertEquals(HttpStatusCode.NoContent, disabled.status)
        val item = service.adminList(1, 20).items.single()
        assertFalse(item.userRssEnabled)
        assertTrue(item.feed.enabled)

        val missing = client.put("/admin/rss/users/missing/enabled") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody("""{"enabled":false}""")
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertTrue(missing.bodyAsText().contains("rss_user_not_found"))
    }

    @Test
    fun `admin inventory reports suspended owners`() = withApp {
        enableRss()
        service.create(USER_ID, RssFeedRequest(name = "Private feed"))
        transaction {
            UsersTable.update({ UsersTable.id eq USER_ID }) { it[suspended] = true }
        }

        val response = client.get("/admin/rss/feeds") { authorize() }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"userSuspended\":true"))
    }

    @Test
    fun `admin inventory rejects malformed pagination`() = withApp {
        val response = client.get("/admin/rss/feeds?page=invalid") { authorize() }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing { adminRssRoutes(service, auth) }
        }
        block()
    }

    private suspend fun enableRss() {
        settings.upsert(AdminSettingsItem(rssEnabled = true, rssPublicBaseUrl = "https://video.example"))
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
    }

    private fun insertUser(id: String, role: String) = transaction {
        UsersTable.insert {
            it[UsersTable.id] = id
            it[email] = role + "@test.local"
            it[passwordHash] = "hash"
            it[name] = role
            it[UsersTable.role] = role
            it[createdAt] = 1L
            it[updatedAt] = 1L
        }
    }
}
