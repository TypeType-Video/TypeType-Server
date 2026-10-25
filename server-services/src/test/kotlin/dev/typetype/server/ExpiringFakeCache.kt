package dev.typetype.server

import dev.typetype.server.cache.CacheService
import java.util.concurrent.ConcurrentHashMap

class ExpiringFakeCache(private val clock: () -> Long = { 0L }) : CacheService {
    private val values = ConcurrentHashMap<String, Pair<String, Long>>()
    val ttls = ConcurrentHashMap<String, Long>()
    var sets = 0
        private set

    override suspend fun get(key: String): String? {
        val (value, expiresAt) = values[key] ?: return null
        if (clock() >= expiresAt) {
            values.remove(key)
            return null
        }
        return value
    }

    override suspend fun set(key: String, value: String, ttlSeconds: Long) {
        sets += 1
        ttls[key] = ttlSeconds
        values[key] = value to clock() + ttlSeconds * 1_000L
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }

    fun keys(): Set<String> = values.keys.toSet()
}
