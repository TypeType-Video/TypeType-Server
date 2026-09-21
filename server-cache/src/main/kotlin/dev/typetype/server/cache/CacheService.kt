package dev.typetype.server.cache

interface CacheService {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String, ttlSeconds: Long)
    suspend fun setIfAbsent(key: String, value: String, ttlSeconds: Long): Boolean =
        throw UnsupportedOperationException("Atomic set-if-absent is not supported")
    suspend fun refreshIfValueMatches(key: String, value: String, ttlSeconds: Long): Boolean =
        throw UnsupportedOperationException("Atomic compare-and-expire is not supported")
    suspend fun delete(key: String)
}
