package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import kotlinx.serialization.Serializable
import java.security.MessageDigest

class SubscriptionFeedSelectionStore(
    private val cache: CacheService,
    private val subscriptions: SubscriptionsService,
) {
    suspend fun resolve(
        userId: String,
        selection: SubscriptionSelection,
        token: String?,
    ): SubscriptionFeedSelectionSnapshot? {
        if (selection == SubscriptionSelection.All) return SubscriptionFeedSelectionSnapshot(null, null)
        if (token == null) {
            val channelUrls = subscriptions.getChannelUrls(userId, selection)
            return SubscriptionFeedSelectionSnapshot(
                token = tokenFor(selection.cursorKey, channelUrls),
                channelUrls = channelUrls,
            )
        }
        for (slot in 0 until MAX_SNAPSHOTS_PER_USER) {
            val stored = read(userId, slot) ?: continue
            if (stored.token != token) continue
            if (stored.filterKey != selection.cursorKey) return null
            return SubscriptionFeedSelectionSnapshot(token, stored.channelUrls.toSet())
        }
        return null
    }

    suspend fun persist(
        userId: String,
        selection: SubscriptionSelection,
        snapshot: SubscriptionFeedSelectionSnapshot,
    ): Boolean {
        val token = snapshot.token ?: return true
        val channelUrls = snapshot.channelUrls ?: return true
        val stored = StoredSubscriptionFeedSelection(
            token = token,
            filterKey = selection.cursorKey,
            channelUrls = channelUrls.sorted(),
        )
        val encoded = CacheJson.encodeToString(StoredSubscriptionFeedSelection.serializer(), stored)
        for (slot in 0 until MAX_SNAPSHOTS_PER_USER) {
            val current = read(userId, slot)
            if (current != null) {
                if (current == stored) {
                    val key = SubscriptionFeedCacheKeys.selection(userId, slot)
                    if (cache.refreshIfValueMatches(key, encoded, SubscriptionFeedSnapshotStore.RETENTION_SECONDS)) {
                        return true
                    }
                }
                continue
            }
            val key = SubscriptionFeedCacheKeys.selection(userId, slot)
            if (cache.setIfAbsent(key, encoded, SubscriptionFeedSnapshotStore.RETENTION_SECONDS)) return true
            if (read(userId, slot) == stored) return true
        }
        return false
    }

    private suspend fun read(userId: String, slot: Int): StoredSubscriptionFeedSelection? {
        val raw = runCatching { cache.get(SubscriptionFeedCacheKeys.selection(userId, slot)) }.getOrNull()
            ?: return null
        return runCatching {
            CacheJson.decodeFromString(StoredSubscriptionFeedSelection.serializer(), raw)
        }.getOrNull()
    }

    private fun tokenFor(filterKey: String, channelUrls: Set<String>): String {
        val identity = CacheJson.encodeToString(
            SubscriptionFeedSelectionIdentity.serializer(),
            SubscriptionFeedSelectionIdentity(filterKey, channelUrls.sorted()),
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_SNAPSHOTS_PER_USER = 8
    }
}

@Serializable
private data class StoredSubscriptionFeedSelection(
    val token: String,
    val filterKey: String,
    val channelUrls: List<String>,
)

@Serializable
private data class SubscriptionFeedSelectionIdentity(
    val filterKey: String,
    val channelUrls: List<String>,
)

data class SubscriptionFeedSelectionSnapshot(
    val token: String?,
    val channelUrls: Set<String>?,
)
