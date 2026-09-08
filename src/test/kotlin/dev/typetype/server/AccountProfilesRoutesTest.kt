package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AccountProfileItem
import dev.typetype.server.routes.accountProfilesRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AuthSessionConfig
import dev.typetype.server.services.ProfileAccountService
import dev.typetype.server.services.ProfileMutationResult
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccountProfilesRoutesTest {
    private val profileService = ProfileAccountService()
    private val auth = AuthService.fixed(TEST_USER_ID)

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
                it[email] = "profiles@test.local"
                it[passwordHash] = "hash"
                it[name] = "Profile"
                it[role] = "user"
                it[createdAt] = 0L
                it[updatedAt] = 0L
            }
        }
    }

    @Test
    fun `profiles can be created renamed and made default`() = withApp {
        val initial = client.get("/profiles") { bearer() }
        assertEquals(HttpStatusCode.OK, initial.status)
        assertTrue(initial.bodyAsText().contains("\"isDefault\":true"))

        val created = client.post("/profiles") {
            bearer()
            contentTypeJson()
            setBody("{\"name\":\"Tech\"}")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val profile = Json.decodeFromString<AccountProfileItem>(created.bodyAsText())

        val renamed = client.put("/profiles/${profile.id}") {
            bearer()
            contentTypeJson()
            setBody("{\"name\":\"Bricolage\"}")
        }
        assertEquals(HttpStatusCode.OK, renamed.status)

        val defaulted = client.post("/profiles/${profile.id}/default") { bearer() }
        assertEquals(HttpStatusCode.OK, defaulted.status)
        val listed = client.get("/profiles") { bearer() }.bodyAsText()
        assertTrue(listed.contains("Bricolage"))
        assertTrue(listed.contains("\"defaultProfileId\":\"${profile.id}\""))
    }

    @Test
    fun `profile routes isolate owners and protect active deletion`() = withApp {
        val created = client.post("/profiles") {
            bearer()
            contentTypeJson()
            setBody("{\"name\":\"Private\"}")
        }
        val profile = Json.decodeFromString<AccountProfileItem>(created.bodyAsText())

        transaction {
            UsersTable.insert {
                it[id] = "other-user-id"
                it[email] = "other@test.local"
                it[passwordHash] = "hash"
                it[name] = "Other"
                it[role] = "user"
                it[createdAt] = 0L
                it[updatedAt] = 0L
            }
        }
        val foreignProfiles = profileService.list("other-user-id")
        assertTrue(foreignProfiles?.profiles?.none { it.id == profile.id } == true)

        val activeDelete = client.delete("/profiles/${profile.id}") { bearer() }
        assertEquals(HttpStatusCode.NoContent, activeDelete.status)
    }

    @Test
    fun `switch returns a new session and active profile cannot be deleted`() = withApp {
        val created = client.post("/profiles") {
            bearer()
            contentTypeJson()
            setBody("{\"name\":\"Travel\"}")
        }
        val profile = Json.decodeFromString<AccountProfileItem>(created.bodyAsText())
        val switched = client.post("/profiles/${profile.id}/switch") { bearer() }
        assertEquals(HttpStatusCode.OK, switched.status)
        assertTrue(switched.bodyAsText().contains("accessToken"))

        assertEquals(ProfileMutationResult.CannotDeleteActive, profileService.delete(profile.id, profile.id))
    }

    @Test
    fun `deleting the default profile promotes the owner profile`() = withApp {
        val created = client.post("/profiles") {
            bearer()
            contentTypeJson()
            setBody("{\"name\":\"Temporary\"}")
        }
        val profile = Json.decodeFromString<AccountProfileItem>(created.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.post("/profiles/${profile.id}/default") { bearer() }.status)
        assertEquals(HttpStatusCode.OK, client.post("/profiles/${profile.id}/switch") { bearer() }.status)
        assertEquals(HttpStatusCode.OK, client.post("/profiles/$TEST_USER_ID/switch") { bearer() }.status)

        assertEquals(ProfileMutationResult.Deleted, profileService.delete(TEST_USER_ID, profile.id))
        val listed = profileService.list(TEST_USER_ID)
        assertEquals(TEST_USER_ID, listed?.defaultProfileId)
    }

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { accountProfilesRoutes(profileService, auth, AuthSessionConfig()) }
        }
        block()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer() {
        headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.contentTypeJson() {
        headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
