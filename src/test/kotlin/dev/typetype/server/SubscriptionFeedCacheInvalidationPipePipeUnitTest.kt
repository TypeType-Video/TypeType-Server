package dev.typetype.server

import dev.typetype.server.services.PipePipeBackupPersisterService
import dev.typetype.server.services.PipePipeBackupSnapshotItem
import dev.typetype.server.services.PipePipeBackupSubscriptionItem
import dev.typetype.server.services.SubscriptionFeedCacheInvalidation
import dev.typetype.server.services.SubscriptionFeedCacheInvalidatorImpl
import dev.typetype.server.services.SubscriptionFeedCacheKeys
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionFeedCacheInvalidationPipePipeUnitTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    private val cache = FakeCacheService()

    @BeforeEach
    fun clean() = runBlocking {
        TestDatabase.truncateAll()
        cache.clear()
        val feed = SubscriptionFeedService(SubscriptionsService(), FakeChannelService(), cache)
        SubscriptionFeedCacheInvalidation.configure(SubscriptionFeedCacheInvalidatorImpl(cache, feed))
    }

    @Test
    fun `restore retains feed snapshot and clears shorts cache`() = runBlocking {
        val userId = TEST_USER_ID
        val feedKey = SubscriptionFeedCacheKeys.feed(userId)
        val shortsKey = SubscriptionFeedCacheKeys.shorts(userId)
        cache.set(feedKey, "warm", 300)
        cache.set(shortsKey, "warm", 300)
        val snapshot = PipePipeBackupSnapshotItem(
            subscriptions = listOf(PipePipeBackupSubscriptionItem(0, "https://yt.com/@x", "X", "")),
            history = emptyList(),
            playlists = emptyList(),
            progress = emptyList(),
            searchHistory = emptyList(),
        )
        PipePipeBackupPersisterService().persist(userId, snapshot)
        assertTrue(SubscriptionsService().getAll(userId).isNotEmpty())
        assertTrue(cache.get(feedKey) != null)
        assertFalse(cache.get(shortsKey) != null)
    }
}
