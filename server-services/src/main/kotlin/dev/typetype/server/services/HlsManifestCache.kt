package dev.typetype.server.services

import dev.typetype.server.cache.CacheService

class HlsManifestCache(private val cache: CacheService) {
    suspend fun get(manifestUrl: String): String? = runCatching {
        cache.get(key(manifestUrl))
    }.getOrNull()

    suspend fun set(manifestUrl: String, manifest: String): Unit {
        runCatching { cache.set(key(manifestUrl), manifest, TTL_SECONDS) }
    }

    private fun key(manifestUrl: String): String = PublicCacheKey.of("hls-manifest", manifestUrl)

    private companion object {
        const val TTL_SECONDS = 30L
    }
}
