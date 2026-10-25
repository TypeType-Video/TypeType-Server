package dev.typetype.server.services

import java.time.Duration

class SabrPreparedInfoCache(
    private val ttl: Duration = Duration.ofMinutes(10),
    maxEntries: Int = 256,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val items = BoundedExpiringCache<Key, Entry>(
        maxEntries = maxEntries,
        ttl = ttl,
        clock = clock,
    )

    fun get(videoId: String, startTimeMs: Long, epoch: Long = 0L): SabrPreparedInfo? {
        val key = Key(videoId, startBucket(startTimeMs))
        val entry = items.get(key) ?: return null
        if (entry.epoch >= epoch && entry.value.initialToken?.isExpired(clock()) != true) return entry.value
        items.remove(key)
        return null
    }

    fun remove(videoId: String, startTimeMs: Long): Unit {
        items.remove(Key(videoId, startBucket(startTimeMs)))
    }

    fun remove(videoId: String): Unit {
        items.removeIf { it.videoId == videoId }
    }

    fun put(videoId: String, startTimeMs: Long, value: SabrPreparedInfo, epoch: Long = 0L): SabrPreparedInfo {
        items.put(Key(videoId, startBucket(startTimeMs)), Entry(value, epoch))
        return value
    }

    fun evictExpired(): Unit = items.evictExpired()

    private fun startBucket(startTimeMs: Long): Long = startTimeMs.coerceAtLeast(0L) / START_BUCKET_MS

    private data class Key(val videoId: String, val startBucket: Long)

    private class Entry(val value: SabrPreparedInfo, val epoch: Long)

    private companion object {
        const val START_BUCKET_MS = 30_000L
    }
}
