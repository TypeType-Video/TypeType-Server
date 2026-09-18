package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

class YouTubeSubtitleCache(private val sharedCache: CacheService?) {
    private val vod = BoundedExpiringCache<String, YouTubeSubtitleContentResult.Ready>(
        maxEntries = 128,
        maxWeight = MAX_MEMORY_BYTES,
        ttl = Duration.ofSeconds(VOD_TTL_SECONDS),
        weigher = { it.content.size.toLong() },
    )
    private val live = BoundedExpiringCache<String, YouTubeSubtitleContentResult.Ready>(
        maxEntries = 32,
        maxWeight = MAX_LIVE_MEMORY_BYTES,
        ttl = Duration.ofSeconds(LIVE_TTL_SECONDS),
        weigher = { it.content.size.toLong() },
    )

    suspend fun get(selection: YouTubeSubtitleSelection): YouTubeSubtitleContentResult.Ready? {
        val key = selection.key()
        vod.get(key)?.let { return it }
        live.get(key)?.let { return it }
        val encoded = runCatching { sharedCache?.get(key) }.getOrNull() ?: return null
        val cached = runCatching { CacheJson.decodeFromString<CachedYouTubeSubtitle>(encoded) }.getOrNull()
            ?: return null
        val ready = cached.toReady(selection.format) ?: return null
        memoryCache(cached.isLive).put(key, ready)
        return ready
    }

    suspend fun put(selection: YouTubeSubtitleSelection, value: YouTubeSubtitleContentResult.Ready) {
        val key = selection.key()
        memoryCache(value.isLive).put(key, value)
        val cached = CachedYouTubeSubtitle(value.content.toString(Charsets.UTF_8), value.isLive)
        runCatching {
            sharedCache?.set(
                key,
                CacheJson.encodeToString(CachedYouTubeSubtitle.serializer(), cached),
                if (value.isLive) LIVE_TTL_SECONDS else VOD_TTL_SECONDS,
            )
        }
    }

    private fun memoryCache(isLive: Boolean) = if (isLive) live else vod

    private fun YouTubeSubtitleSelection.key(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cacheKey.encodeToByteArray())
        return "$CACHE_PREFIX:${HexFormat.of().formatHex(digest)}"
    }

    @Serializable
    private data class CachedYouTubeSubtitle(val content: String, val isLive: Boolean) {
        fun toReady(format: YouTubeSubtitleFormat): YouTubeSubtitleContentResult.Ready? {
            val bytes = content.encodeToByteArray()
            return if (isValidSubtitlePayload(bytes, format)) {
                YouTubeSubtitleContentResult.Ready(bytes, format, isLive)
            } else {
                null
            }
        }
    }

    private companion object {
        const val CACHE_PREFIX = "youtube-subtitle:v1"
        const val VOD_TTL_SECONDS = 21_600L
        const val LIVE_TTL_SECONDS = 5L
        const val MAX_MEMORY_BYTES = 64L * 1024 * 1024
        const val MAX_LIVE_MEMORY_BYTES = 8L * 1024 * 1024
    }
}
