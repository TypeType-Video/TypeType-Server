package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult

class CachedNativeManifestService(
    private val delegate: NativeManifestService,
    private val cache: CacheService,
) {

    suspend fun nativeManifest(videoUrl: String): ExtractionResult<String> {
        val key = "native-manifest:${CachedStreamService.cacheKey(videoUrl)}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return ExtractionResult.Success(cached)
        }
        val result = delegate.nativeManifest(videoUrl)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, result.data, NATIVE_MANIFEST_TTL_SECONDS) }
        }
        return result
    }

    private companion object {
        const val NATIVE_MANIFEST_TTL_SECONDS = 21600L
    }
}
