package dev.typetype.server.services

import dev.typetype.server.models.StreamResponse
import java.time.Duration

class SabrBootstrapCache(
    private val epoch: () -> Long = { 0L },
    private val preparedInfoAvailable: (String) -> Boolean = { true },
    ttl: Duration = Duration.ofMinutes(10),
    maxEntries: Int = 256,
    clock: () -> Long = System::currentTimeMillis,
) {
    private val items = BoundedExpiringCache<String, Entry>(maxEntries = maxEntries, ttl = ttl, clock = clock)

    fun get(videoId: String): StreamResponse? {
        val entry = items.get(videoId) ?: return null
        if (entry.epoch == epoch() && preparedInfoAvailable(videoId)) return entry.response
        items.remove(videoId)
        return null
    }

    fun put(videoId: String, response: StreamResponse, observedEpoch: Long): Unit {
        if (response.isLive || observedEpoch != epoch()) return
        items.put(videoId, Entry(response, observedEpoch))
    }

    fun currentEpoch(): Long = epoch()

    private class Entry(val response: StreamResponse, val epoch: Long)
}
