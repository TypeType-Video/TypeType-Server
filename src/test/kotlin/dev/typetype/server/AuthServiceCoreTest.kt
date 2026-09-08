package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.db.tables.SessionsTable
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AuthSessionConfig
import dev.typetype.server.services.ProfileAccountService
import dev.typetype.server.services.ProfileMutationResult
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthServiceCoreTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `register sets first admin and second user`() = runTest {
        val service = AuthService("test-secret")
        assertFalse(service.hasUsers())
        assertFalse(service.hasAdmin())

        val session1 = service.register("first@test.local", "secret-1", "First")
        val user1 = service.verify(session1.accessToken)
        assertNotNull(user1)
        val firstUserId = user1 ?: error("missing first user")
        assertEquals("admin", roleOf(firstUserId))
        assertEquals("First", usernameOf(firstUserId))
        assertNotNull(service.login("first", "secret-1"))
        assertTrue(service.hasUsers())
        assertTrue(service.hasAdmin())

        val session2 = service.register("second@test.local", "secret-2", "Second")
        val user2 = service.verify(session2.accessToken)
        assertNotNull(user2)
        assertEquals("user", user2?.let { roleOf(it) })

        transaction {
            UsersTable.update({ UsersTable.id eq firstUserId }) {
                it[role] = "user"
            }
        }
        assertFalse(service.hasAdmin())

        val session3 = service.register("third@test.local", "secret-3", "Third")
        val user3 = service.verify(session3.accessToken)
        assertEquals("admin", user3?.let { roleOf(it) })
    }

    @Test
    fun `login and refresh token keep same user`() = runTest {
        val service = AuthService("test-secret")
        val registered = service.register("login@test.local", "secret-1", "Login")
        val expectedUser = service.verify(registered.accessToken)

        val loginToken = service.login("login@test.local", "secret-1")
        assertNotNull(loginToken)
        assertEquals(expectedUser, loginToken?.let { service.verify(it.accessToken) })
        assertNull(service.login("login@test.local", "wrong"))

        val refreshed = service.refreshSession(registered.refreshToken)
        assertNotNull(refreshed)
        assertEquals(expectedUser, refreshed?.let { service.verify(it.accessToken) })
    }

    @Test
    fun `configured refresh lifetime is stored for new sessions`() = runTest {
        val before = System.currentTimeMillis()
        val service = AuthService(
            "test-secret",
            sessionConfig = AuthSessionConfig(refreshTtlDays = 3),
        )

        service.register("ttl@test.local", "secret-1", "TTL")
        val expiresAt = transaction {
            SessionsTable.selectAll().single()[SessionsTable.expiresAt]
        }

        val expected = before + 3L * 24L * 60L * 60L * 1000L
        assertTrue(expiresAt in expected..(expected + 5_000L))
    }

    @Test
    fun `login supports public username identifier`() = runTest {
        val service = AuthService("test-secret")
        val session = service.register("username@test.local", "secret-1", "User")
        val userId = service.verify(session.accessToken) ?: error("missing user id")
        transaction {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[publicUsername] = "InfinityLoop1308"
            }
        }
        val byUsername = service.login("InfinityLoop1308", "secret-1")
        assertNotNull(byUsername)
        assertEquals(userId, byUsername?.let { service.verify(it.accessToken) })
    }

    @Test
    fun `secondary profile credentials cannot be used as a local login`() = runTest {
        val profiles = ProfileAccountService()
        val service = AuthService("test-secret", profileAccountService = profiles)
        val ownerSession = service.register("profile-owner@test.local", "secret-1", "Owner")
        val ownerId = service.verify(ownerSession.accessToken) ?: error("missing owner id")
        val child = profiles.create(ownerId, "Child")
        val childId = (child as ProfileMutationResult.Success).profile.id

        assertNull(service.login("profile-$childId@profiles.invalid", "profile:$childId"))
    }

    @Test
    fun `guest token verifies and has user role`() = runTest {
        val service = AuthService("test-secret")
        val guestToken = service.guestLogin()
        val guestId = service.verify(guestToken)
        assertNotNull(guestId)
        assertTrue(guestId?.startsWith("guest:") == true)
        assertEquals("user", guestId?.let { service.getUserRole(it) })
    }

    private fun roleOf(userId: String): String? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.get(UsersTable.role)
    }

    private fun usernameOf(userId: String): String? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.get(UsersTable.publicUsername)
    }
}
