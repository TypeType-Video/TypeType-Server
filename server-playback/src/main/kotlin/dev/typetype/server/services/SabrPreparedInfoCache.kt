package dev.typetype.server.services

import java.time.Duration

class SabrPreparedInfoCache(
    private val ttl: Duration = Duration.ofMinutes(10),
    maxEntries: Int = 256,
    clock: () -> Long = System::currentTimeMillis,
) {
    private val items = BoundedExpiringCache<Key, SabrPreparedInfo>(
        maxEntries = maxEntries,
        ttl = ttl,
        clock = clock,
    )

    fun get(videoId: String, startTimeMs: Long): SabrPreparedInfo? {
        return items.get(Key(videoId, startBucket(startTimeMs)))
    }

    fun remove(videoId: String, startTimeMs: Long): Unit {
        items.remove(Key(videoId, startBucket(startTimeMs)))
    }

    fun remove(videoId: String): Unit {
        items.removeIf { it.videoId == videoId }
    }

    fun put(videoId: String, startTimeMs: Long, value: SabrPreparedInfo): SabrPreparedInfo {
        items.put(Key(videoId, startBucket(startTimeMs)), value)
        return value
    }

    fun evictExpired(): Unit = items.evictExpired()

    private fun startBucket(startTimeMs: Long): Long = startTimeMs.coerceAtLeast(0L) / START_BUCKET_MS

    private data class Key(val videoId: String, val startBucket: Long)

    private companion object {
        const val START_BUCKET_MS = 30_000L
    }
}
