package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.VideoItem
import kotlinx.serialization.builtins.ListSerializer

class SubscriptionFeedSnapshotStore(
    private val cache: CacheService,
    private val clock: () -> Long,
) {
    suspend fun current(userId: String): SubscriptionFeedSnapshot? {
        val raw = runCatching { cache.get(SubscriptionFeedCacheKeys.feed(userId)) }.getOrNull() ?: return null
        decodeSnapshot(raw)?.let { return it }
        val legacyVideos = runCatching {
            CacheJson.decodeFromString(ListSerializer(VideoItem.serializer()), raw)
        }.getOrNull() ?: return null
        return SubscriptionFeedSnapshot(
            generation = clock().coerceAtLeast(1L),
            generatedAt = clock(),
            stale = true,
            videos = legacyVideos,
        ).also { runCatching { writeCurrent(userId, it) } }
    }

    suspend fun previous(userId: String): SubscriptionFeedSnapshot? = read(
        SubscriptionFeedCacheKeys.previousFeed(userId),
    )

    suspend fun invalidationToken(userId: String): String? = runCatching {
        cache.get(SubscriptionFeedCacheKeys.invalidation(userId))
    }.getOrNull()

    suspend fun invalidate(userId: String, token: String) {
        cache.set(SubscriptionFeedCacheKeys.invalidation(userId), token, RETENTION_SECONDS)
        current(userId)?.takeUnless { it.stale }?.let { writeCurrent(userId, it.copy(stale = true)) }
    }

    suspend fun markStale(userId: String) {
        current(userId)?.takeUnless { it.stale }?.let { writeCurrent(userId, it.copy(stale = true)) }
    }

    suspend fun publish(userId: String, snapshot: SubscriptionFeedSnapshot) {
        current(userId)?.let {
            cache.set(
                SubscriptionFeedCacheKeys.previousFeed(userId),
                CacheJson.encodeToString(SubscriptionFeedSnapshot.serializer(), it),
                RETENTION_SECONDS,
            )
        }
        writeCurrent(userId, snapshot)
    }

    private suspend fun read(key: String): SubscriptionFeedSnapshot? {
        val raw = runCatching { cache.get(key) }.getOrNull() ?: return null
        return decodeSnapshot(raw)
    }

    private fun decodeSnapshot(raw: String): SubscriptionFeedSnapshot? = runCatching {
        CacheJson.decodeFromString(SubscriptionFeedSnapshot.serializer(), raw)
    }.getOrNull()

    private suspend fun writeCurrent(userId: String, snapshot: SubscriptionFeedSnapshot) {
        cache.set(
            SubscriptionFeedCacheKeys.feed(userId),
            CacheJson.encodeToString(SubscriptionFeedSnapshot.serializer(), snapshot),
            RETENTION_SECONDS,
        )
    }

    companion object {
        const val RETENTION_SECONDS = 24 * 60 * 60L
    }
}
