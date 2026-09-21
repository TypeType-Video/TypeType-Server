package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.services.SubscriptionFeedCacheKeys
import dev.typetype.server.services.SubscriptionFeedSnapshot
import dev.typetype.server.services.SubscriptionFeedSnapshotStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionFeedSnapshotStoreTest {
    @Test
    fun `published snapshots retain current and previous generations for one day`() = runTest {
        val cache = RecordingCache()
        val store = SubscriptionFeedSnapshotStore(cache) { 10_000L }
        val first = SubscriptionFeedSnapshot(1, 1_000, stale = false, listOf(video(1000L)))
        val second = SubscriptionFeedSnapshot(2, 2_000, stale = false, listOf(video(2000L)))

        store.publish("user", first)
        store.publish("user", second)

        assertEquals(second, store.current("user"))
        assertEquals(first, store.previous("user"))
        assertTrue(cache.ttls.values.all { it == 86_400L })
    }

    @Test
    fun `legacy list becomes a retained stale snapshot`() = runTest {
        val cache = RecordingCache()
        val raw = CacheJson.encodeToString(
            ListSerializer(dev.typetype.server.models.VideoItem.serializer()),
            listOf(video(1000L)),
        )
        cache.set(SubscriptionFeedCacheKeys.feed("user"), raw, 60)
        val store = SubscriptionFeedSnapshotStore(cache) { 10_000L }

        val migrated = store.current("user")

        assertNotNull(migrated)
        assertTrue(requireNotNull(migrated).stale)
        assertEquals(86_400L, cache.ttls[SubscriptionFeedCacheKeys.feed("user")])
    }

    @Test
    fun `snapshot without live promotion metadata remains readable`() = runTest {
        val cache = RecordingCache()
        val raw = """
            {"generation":1,"generatedAt":1000,"stale":false,"videos":[]}
        """.trimIndent()
        cache.set(SubscriptionFeedCacheKeys.feed("user"), raw, 60)
        val store = SubscriptionFeedSnapshotStore(cache) { 10_000L }

        assertEquals(emptyMap<String, Long>(), store.current("user")?.livePromotedAt)
    }

    private class RecordingCache : CacheService {
        val values = mutableMapOf<String, String>()
        val ttls = mutableMapOf<String, Long>()

        override suspend fun get(key: String): String? = values[key]

        override suspend fun set(key: String, value: String, ttlSeconds: Long) {
            values[key] = value
            ttls[key] = ttlSeconds
        }

        override suspend fun delete(key: String) {
            values.remove(key)
            ttls.remove(key)
        }
    }
}
