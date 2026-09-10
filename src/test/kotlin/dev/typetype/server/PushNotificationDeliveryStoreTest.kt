package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.services.PushNotificationDeliveryStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PushNotificationDeliveryStoreTest {
    private val store = PushNotificationDeliveryStore { 1_000L }

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `does not create historical delivery and retries an existing pending delivery`() = runTest {
        assertFalse(DatabaseFactory.query { store.claim("event", "user", "device", false, 1_000L) })
        assertTrue(DatabaseFactory.query { store.claim("event", "user", "device", true, 1_000L) })
        assertFalse(DatabaseFactory.query { store.claim("event", "user", "device", false, 1_001L) })
        assertTrue(DatabaseFactory.query { store.claim("event", "user", "device", false, 31_001L) })
    }
}
