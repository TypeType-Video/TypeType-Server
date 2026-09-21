package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

class CachedStreamService(
    private val delegate: StreamService,
    private val cache: CacheService,
    private val cachePrefix: String = DEFAULT_CACHE_PREFIX,
) : StreamService {

    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ExtractionResult<StreamResponse>>>()

    companion object {
        private const val DEFAULT_CACHE_PREFIX = "stream:v6"

        fun cacheKey(url: String): String = "$DEFAULT_CACHE_PREFIX:$url"
    }

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        val key = "$cachePrefix:$url"
        cachedStream(key)?.let { return it }
        val pending = CompletableDeferred<ExtractionResult<StreamResponse>>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return existing.await()
        return try {
            val result = getCachedOrLoad(url, key)
            pending.complete(result)
            result
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    private suspend fun getCachedOrLoad(url: String, key: String): ExtractionResult<StreamResponse> {
        cachedStream(key)?.let { return it }
        val result = delegate.getStreamInfo(url)
        if (result is ExtractionResult.Success) {
            val ttl = result.data.streamCacheTtlSeconds()
            if (ttl > 0) {
                runCatching {
                    cache.set(key, CacheJson.encodeToString(StreamResponse.serializer(), result.data), ttl)
                }
            }
        }
        return result
    }

    private suspend fun cachedStream(key: String): ExtractionResult<StreamResponse>? = runCatching { cache.get(key) }
        .getOrNull()
        ?.let { cached ->
            runCatching {
                ExtractionResult.Success(CacheJson.decodeFromString<StreamResponse>(cached))
            }.getOrNull()
        }
}
