package dev.typetype.server

import dev.typetype.server.cache.CacheService
import java.util.concurrent.ConcurrentHashMap

class FakeCacheService : CacheService {
    private val values = ConcurrentHashMap<String, String>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun set(key: String, value: String, ttlSeconds: Long) {
        values[key] = value
    }

    override suspend fun setIfAbsent(key: String, value: String, ttlSeconds: Long): Boolean =
        values.putIfAbsent(key, value) == null

    override suspend fun refreshIfValueMatches(key: String, value: String, ttlSeconds: Long): Boolean {
        var matched = false
        values.computeIfPresent(key) { _, current ->
            matched = current == value
            current
        }
        return matched
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }

    fun clear() {
        values.clear()
    }

    fun keys(): Set<String> = values.keys.toSet()
}
