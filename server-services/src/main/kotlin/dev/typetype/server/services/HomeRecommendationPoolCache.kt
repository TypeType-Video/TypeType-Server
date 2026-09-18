package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.HomeRecommendationPool
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

class HomeRecommendationPoolCache(private val cache: dev.typetype.server.cache.CacheService) {
    suspend fun read(key: String): HomeRecommendationPool? {
        val raw = runCatching { cache.get(key) }.getOrNull() ?: return null
        return runCatching { CacheJson.decodeFromString<HomeRecommendationPool>(raw) }.getOrNull()
    }

    suspend fun readStale(key: String): HomeRecommendationPool? {
        val raw = runCatching { cache.get(staleKey(key)) }.getOrNull() ?: return null
        return runCatching { CacheJson.decodeFromString<HomeRecommendationPool>(raw) }.getOrNull()
    }

    suspend fun write(key: String, pool: HomeRecommendationPool) {
        val raw = CacheJson.encodeToString(pool)
        runCatching { cache.set(key, raw, CACHE_TTL_SECONDS) }
        runCatching { cache.set(staleKey(key), raw, STALE_TTL_SECONDS) }
    }

    suspend fun delete(userId: String, serviceId: Int, mode: HomeRecommendationPoolMode) {
        val key = key(userId, serviceId, mode, personalizationEnabled = false)
        runCatching { cache.delete(key) }
        runCatching { cache.delete(staleKey(key)) }
    }

    fun key(userId: String, serviceId: Int, mode: HomeRecommendationPoolMode, personalizationEnabled: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val seed = "$CACHE_VERSION:$userId:$serviceId:$mode:$personalizationEnabled"
        val hex = digest.digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
        return "recommendations:home:$hex"
    }

    private fun staleKey(key: String): String = "$key:stale"

    companion object {
        private const val CACHE_TTL_SECONDS = 3_600L
        private const val STALE_TTL_SECONDS = 86_400L
        private const val CACHE_VERSION = 10
    }
}
