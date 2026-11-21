package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.TwitchAccountsTable
import dev.typetype.server.services.TwitchAccount
import dev.typetype.server.services.TwitchAccountStore
import dev.typetype.server.services.TwitchTokenCrypto
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TwitchAccountStoreTest {
    private val store = TwitchAccountStore(TwitchTokenCrypto("k".repeat(32)))

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() = runTest { DatabaseFactory.query { TwitchAccountsTable.deleteAll() } }

    @Test
    fun `tokens are encrypted at rest and read back`() = runTest {
        store.save(TEST_USER_ID, TwitchAccount("access-1", "refresh-1", "me", "Me", "", 10))
        val raw = DatabaseFactory.query { TwitchAccountsTable.selectAll().single() }
        assertFalse(raw[TwitchAccountsTable.encryptedAccessToken].contains("access-1"))
        assertEquals("access-1", store.get(TEST_USER_ID)?.accessToken)
        store.save(TEST_USER_ID, TwitchAccount("access-2", null, "me", "Me", "", 20))
        val updated = store.get(TEST_USER_ID)
        assertEquals("access-2", updated?.accessToken)
        assertNull(updated?.refreshToken)
        assertEquals(true, store.delete(TEST_USER_ID))
        assertNull(store.get(TEST_USER_ID))
    }
}
