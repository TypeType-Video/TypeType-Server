package dev.typetype.server

import dev.typetype.server.services.SubscriptionFeedSelectionSnapshot
import dev.typetype.server.services.SubscriptionFeedSelectionStore
import dev.typetype.server.services.SubscriptionSelection
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionFeedSelectionStoreTest {
    @Test
    fun `a ninth distinct session cannot evict the first issued cursor`() = runTest {
        val cache = FakeCacheService()
        val store = SubscriptionFeedSelectionStore(cache, SubscriptionsService())
        val selections = (1..9).map { index ->
            val selection = SubscriptionSelection.Group("group-$index")
            val snapshot = SubscriptionFeedSelectionSnapshot(
                token = index.toString().padStart(64, '0'),
                channelUrls = setOf("https://example.com/channel/$index"),
            )
            selection to snapshot
        }

        selections.take(8).forEach { (selection, snapshot) ->
            assertTrue(store.persist(TEST_USER_ID, selection, snapshot))
        }
        val (ninthSelection, ninthSnapshot) = selections.last()
        assertFalse(store.persist(TEST_USER_ID, ninthSelection, ninthSnapshot))

        val (firstSelection, firstSnapshot) = selections.first()
        val restored = store.resolve(TEST_USER_ID, firstSelection, firstSnapshot.token)
        assertNotNull(restored)
        assertEquals(firstSnapshot.channelUrls, restored?.channelUrls)
        assertEquals(8, cache.keys().count { it.startsWith("feed:selection") })
    }

    @Test
    fun `independent store instances cannot overwrite concurrently issued cursors`() = runTest {
        val cache = FakeCacheService()
        val stores = List(2) { SubscriptionFeedSelectionStore(cache, SubscriptionsService()) }
        val selections = List(2) { index ->
            val number = index + 1
            val selection = SubscriptionSelection.Group("group-$number")
            val snapshot = SubscriptionFeedSelectionSnapshot(
                token = number.toString().padStart(64, '0'),
                channelUrls = setOf("https://example.com/channel/$number"),
            )
            selection to snapshot
        }
        val start = CompletableDeferred<Unit>()
        val writes = stores.zip(selections).map { (store, pair) ->
            async(Dispatchers.Default) {
                start.await()
                store.persist(TEST_USER_ID, pair.first, pair.second)
            }
        }

        start.complete(Unit)

        assertTrue(writes.awaitAll().all { it })
        selections.forEachIndexed { index, (selection, snapshot) ->
            assertNotNull(stores[index].resolve(TEST_USER_ID, selection, snapshot.token))
        }
    }
}
