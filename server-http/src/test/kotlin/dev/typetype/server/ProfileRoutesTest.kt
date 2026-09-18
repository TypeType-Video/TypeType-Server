package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.routes.profileRoutes
import dev.typetype.server.routes.customAvatarRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.CustomAvatarService
import dev.typetype.server.services.ProfileService
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProfileRoutesTest {

    private val auth = AuthService.fixed(TEST_USER_ID)
    private val profileService = ProfileService()
    private val avatarService = AvatarService()
    private val customAvatarService = CustomAvatarService()

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
                it[email] = "profile@test.local"
                it[passwordHash] = "hash"
                it[name] = "Profile"
                it[role] = "user"
                it[createdAt] = 0L
                it[updatedAt] = 0L
            }
        }
    }

    @Test
    fun `PUT emoji avatar stores normalized OpenMoji code`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { profileRoutes(profileService, avatarService, customAvatarService, auth) }
        }

        val response = client.put("/profile/avatar/emoji") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"code":"1f60a"}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)

        val row = transaction { UsersTable.selectAll().where { UsersTable.id eq TEST_USER_ID }.single() }
        assertEquals("emoji", row[UsersTable.avatarType])
        assertEquals("1F60A", row[UsersTable.avatarCode])
        assertEquals("/avatar/openmoji/1F60A.svg", row[UsersTable.avatarUrl])
    }

    @Test
    fun `PUT custom avatar stores GIF upload`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { customAvatarRoutes(customAvatarService, auth) }
        }

        val response = client.put("/profile/avatar/custom") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, "image/gif")
            setBody("GIF89a-avatar".encodeToByteArray())
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val row = transaction { UsersTable.selectAll().where { UsersTable.id eq TEST_USER_ID }.single() }
        assertEquals("custom", row[UsersTable.avatarType])
    }

    @Test
    fun `DELETE avatar clears avatar fields`() = testApplication {
        transaction {
            UsersTable.update({ UsersTable.id eq TEST_USER_ID }) {
                it[avatarType] = "emoji"
                it[avatarCode] = "1F60A"
                it[avatarUrl] = "/avatar/openmoji/1F60A.svg"
            }
        }

        application {
            install(ContentNegotiation) { json() }
            routing { profileRoutes(profileService, avatarService, customAvatarService, auth) }
        }

        val response = client.delete("/profile/avatar") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
