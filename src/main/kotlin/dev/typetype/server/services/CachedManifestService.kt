package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import java.net.URI

class CachedManifestService(
    private val delegate: ManifestService,
    private val cache: CacheService,
) {

    suspend fun dashManifest(videoUrl: String): ExtractionResult<String> {
        if (!isCacheable(videoUrl)) return delegate.dashManifest(videoUrl)
        val key = "dash-manifest-v2:${CachedStreamService.cacheKey(videoUrl)}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return ExtractionResult.Success(cached)
        }
        val result = delegate.dashManifest(videoUrl)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, result.data, DASH_MANIFEST_TTL_SECONDS) }
        }
        return result
    }

    private companion object {
        const val DASH_MANIFEST_TTL_SECONDS = 21600L

        fun isCacheable(videoUrl: String): Boolean {
            val host = runCatching { URI(videoUrl).host.orEmpty().lowercase().trimEnd('.') }.getOrDefault("")
            return host != "b23.tv" && host != "nico.ms" &&
                !host.endsWith(".bilibili.com") && host != "bilibili.com" &&
                !host.endsWith(".nicovideo.jp") && host != "nicovideo.jp"
        }
    }
}
