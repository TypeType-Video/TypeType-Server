package dev.typetype.server

import com.password4j.Password
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.services.AccountIdentityService
import dev.typetype.server.services.AccountIdentityUpdateResult
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.eq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccountIdentityServiceTest {
    private val service = AccountIdentityService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit {
        TestDatabase.truncateAll()
        insertUser(TEST_USER_ID, "old@test.local", "secret")
        insertUser("taken", "taken@test.local", "secret")
    }

    @Test
    fun `local user changes email and name with current password`() = runBlocking {
        val result = service.updateSelf(TEST_USER_ID, " NEW@Test.Local ", " New Name ", "secret")
        assertEquals(AccountIdentityUpdateResult.Updated, result)
        assertEquals("new@test.local", service.get(TEST_USER_ID)?.email)
        assertEquals("New Name", service.get(TEST_USER_ID)?.name)
        val verified = transaction {
            UsersTable.selectAll().where { UsersTable.id eq TEST_USER_ID }.single()[UsersTable.verified]
        }
        assertEquals(false, verified)
    }

    @Test
    fun `rejects invalid password and case insensitive email conflict`() = runBlocking {
        assertEquals(
            AccountIdentityUpdateResult.InvalidPassword,
            service.updateSelf(TEST_USER_ID, "new@test.local", "Name", "wrong"),
        )
        assertEquals(
            AccountIdentityUpdateResult.EmailTaken,
            service.updateSelf(TEST_USER_ID, "TAKEN@test.local", "Name", "secret"),
        )
    }

    private fun insertUser(id: String, email: String, password: String): Unit = transaction {
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.email] = email
            it[passwordHash] = Password.hash(password).withArgon2().result
            it[name] = "Name"
            it[role] = "user"
            it[verified] = true
            it[createdAt] = 0
            it[updatedAt] = 0
        }
    }
}
