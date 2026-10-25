package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class CachedStreamService(
    private val delegate: StreamService,
    private val cache: CacheService,
    private val cachePrefix: String = DEFAULT_CACHE_PREFIX,
    private val watch: StreamRequestWatch? = null,
    private val pace: StreamOpenPace? = null,
    private val renewal: ProviderSessionRenewal? = null,
    private val failures: StreamFailureMemory = StreamFailureMemory(),
) : StreamService {

    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ExtractionResult<StreamResponse>>>()

    companion object {
        private const val DEFAULT_CACHE_PREFIX = "stream:v6"

        const val PACED_CODE = "provider_paced"
        const val PACED_RETRY_AFTER_SECONDS = 30L

        fun cacheKey(url: String): String = "$DEFAULT_CACHE_PREFIX:$url"
    }

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        val key = "$cachePrefix:$url"
        cachedStream(key)?.let { return it }
        failures.recall(key)?.let { return it }
        val pending = CompletableDeferred<ExtractionResult<StreamResponse>>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return awaitShared(url, existing)
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

    private suspend fun awaitShared(
        url: String,
        shared: CompletableDeferred<ExtractionResult<StreamResponse>>,
    ): ExtractionResult<StreamResponse> = try {
        shared.await()
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
        getStreamInfo(url)
    }

    private suspend fun getCachedOrLoad(url: String, key: String): ExtractionResult<StreamResponse> {
        cachedStream(key)?.let { return it }
        val nobodyWaits = coroutineContext[StreamRequestWatch.NobodyWaiting] != null
        val counted = coroutineContext[StreamRequestWatch.AlreadyCounted] != null
        if (nobodyWaits && !backgroundAdmitted(counted)) {
            return paced("The provider is not asked for this video now")
        }
        if (pace != null && isYoutubeUrl(url) && !pace.turn(someoneWaits = !nobodyWaits)) {
            return paced("Too many videos are being opened at the provider right now")
        }
        val result = delegate.getStreamInfo(url)
        if (nobodyWaits && !counted) {
            withContext(StreamRequestWatch.AlreadyCounted.mark) { watch?.opened(result) }
        } else {
            watch?.opened(result)
        }
        if ((result as? ExtractionResult.Failure)?.kind == ExtractionFailureKind.ProviderAccessBlocked) renewal?.refused(url)
        failures.remember(key, result)
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

    private fun paced(message: String): ExtractionResult.Failure =
        ExtractionResult.Failure(message, PACED_CODE, ExtractionFailureKind.RateLimited, PACED_RETRY_AFTER_SECONDS)

    private suspend fun backgroundAdmitted(counted: Boolean): Boolean {
        val watch = watch ?: return true
        return if (counted) !watch.paused() else watch.admit()
    }

    private suspend fun cachedStream(key: String): ExtractionResult<StreamResponse>? = runCatching { cache.get(key) }
        .getOrNull()
        ?.let { cached ->
            runCatching {
                ExtractionResult.Success(CacheJson.decodeFromString<StreamResponse>(cached))
            }.getOrNull()
        }
}
