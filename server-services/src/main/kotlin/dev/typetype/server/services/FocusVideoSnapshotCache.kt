package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class FocusVideoSnapshotCache(
    private val cache: CacheService? = null,
) {
    suspend fun put(videoKey: String, snapshot: FocusVideoSnapshot) {
        val store = cache ?: return
        if (videoKey.isBlank()) return
        val encoded = CacheJson.encodeToString(FocusVideoSnapshot.serializer(), snapshot)
        runCatching { store.set(cacheKey(videoKey), encoded, TTL_SECONDS) }
    }

    suspend fun get(videoKey: String): FocusVideoSnapshot? {
        val store = cache ?: return null
        if (videoKey.isBlank()) return null
        val raw = runCatching { store.get(cacheKey(videoKey)) }.getOrNull() ?: return null
        return runCatching { CacheJson.decodeFromString(FocusVideoSnapshot.serializer(), raw) }.getOrNull()
    }

    private fun cacheKey(videoKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(videoKey.toByteArray(StandardCharsets.UTF_8))
        val hex = digest.joinToString(separator = "") { "%02x".format(it) }
        return "$PREFIX$hex"
    }

    companion object {
        private const val PREFIX = "focus:video-snapshot:v1:"
        private const val TTL_SECONDS = 900L
    }
}
