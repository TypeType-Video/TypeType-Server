package dev.typetype.server

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.YoutubeTakeoutCommitPlan
import dev.typetype.server.models.YoutubeTakeoutParsedData
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.SubscriptionFeedCacheInvalidation
import dev.typetype.server.services.SubscriptionFeedCacheInvalidatorImpl
import dev.typetype.server.services.SubscriptionFeedCacheKeys
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.WatchLaterService
import dev.typetype.server.services.YoutubeTakeoutImporterService
import dev.typetype.server.services.YoutubeTakeoutSignalImportService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionFeedCacheInvalidationTakeoutUnitTest {
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
    fun `takeout with subscriptions retains feed and clears shorts cache`() = runBlocking {
        val userId = TEST_USER_ID
        val feedKey = SubscriptionFeedCacheKeys.feed(userId)
        val shortsKey = SubscriptionFeedCacheKeys.shorts(userId)
        cache.set(feedKey, "warm", 300)
        cache.set(shortsKey, "warm", 300)
        importer().commit(userId, parsed(), withSubscriptions = true)
        assertTrue(cache.get(feedKey) != null)
        assertFalse(cache.get(shortsKey) != null)
    }

    @Test
    fun `takeout without subscriptions keeps feed and shorts cache keys`() = runBlocking {
        val userId = TEST_USER_ID
        val feedKey = SubscriptionFeedCacheKeys.feed(userId)
        val shortsKey = SubscriptionFeedCacheKeys.shorts(userId)
        cache.set(feedKey, "warm", 300)
        cache.set(shortsKey, "warm", 300)
        importer().commit(userId, parsed(), withSubscriptions = false)
        assertTrue(cache.get(feedKey) != null)
        assertTrue(cache.get(shortsKey) != null)
    }

    private fun importer(): YoutubeTakeoutImporterService {
        val subscriptions = SubscriptionsService()
        val playlists = PlaylistService()
        val history = HistoryService()
        val signalImport = YoutubeTakeoutSignalImportService(FavoritesService(), WatchLaterService(), history)
        return YoutubeTakeoutImporterService(subscriptions, playlists, signalImport)
    }

    private fun parsed(): YoutubeTakeoutParsedData = YoutubeTakeoutParsedData(
        subscriptions = listOf(SubscriptionItem("https://youtube.com/channel/UC123", "Name", "")),
        playlists = listOf(PlaylistItem(id = "PL1", name = "P1", description = "")),
        playlistItems = emptyMap(),
        favorites = emptyList(),
        watchLater = emptyList(),
        history = emptyList(),
        warnings = emptyList(),
        errors = emptyList(),
    )

    private suspend fun YoutubeTakeoutImporterService.commit(userId: String, parsed: YoutubeTakeoutParsedData, withSubscriptions: Boolean) {
        val plan = YoutubeTakeoutCommitPlan(
            importSubscriptions = withSubscriptions,
            importPlaylists = false,
            importPlaylistItems = false,
            importFavorites = false,
            importWatchLater = false,
            importHistory = false,
        )
        commit(userId, parsed, plan)
    }
}
