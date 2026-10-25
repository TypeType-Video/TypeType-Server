package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import org.slf4j.LoggerFactory

class PublicCacheStore<T>(
    private val cache: CacheService,
    private val area: PublicCacheArea<T>,
    private val key: String,
) {
    private val goodKey = "$key$GOOD_SUFFIX"
    private val backoffKey = "$key$BACKOFF_SUFFIX"

    suspend fun primary(): T? = read(key, area.serializer)

    suspend fun lastGood(): T? = if (area.keepLastGood) {
        read(goodKey, PublicCacheEnvelope.serializer(area.serializer))?.value
    } else {
        null
    }

    suspend fun backoff(): PublicCacheBackoff? =
        if (area.backoffOnFailure) read(backoffKey, PublicCacheBackoff.serializer()) else null

    suspend fun storePrimary(value: T, ttlSeconds: Long) {
        if (ttlSeconds <= 0L) return
        if (write(key, CacheJson.encodeToString(area.serializer, value), ttlSeconds)) {
            logger.debug("cache event=store area={} key={} ttlSeconds={}", area.name, key, ttlSeconds)
        }
    }

    suspend fun storeLastGood(value: T, now: Long) {
        if (!area.keepLastGood) return
        val envelope = PublicCacheEnvelope(value, now)
        write(goodKey, CacheJson.encodeToString(PublicCacheEnvelope.serializer(area.serializer), envelope), GOOD_TTL_SECONDS)
    }

    suspend fun storeBackoff(backoff: PublicCacheBackoff) {
        if (!area.backoffOnFailure) return
        write(backoffKey, CacheJson.encodeToString(PublicCacheBackoff.serializer(), backoff), PublicCacheBackoff.RETENTION_SECONDS)
    }

    suspend fun clearBackoff() {
        guarded("delete_failed", backoffKey) { cache.delete(backoffKey) }
    }

    private suspend fun <V> read(cacheKey: String, serializer: KSerializer<V>): V? {
        val raw = guarded("get_failed", cacheKey) { cache.get(cacheKey) } ?: return null
        val decoded = runCatching { CacheJson.decodeFromString(serializer, raw) }
        decoded.exceptionOrNull()?.let {
            logger.warn("cache event=decode_failed area={} key={} error={}", area.name, cacheKey, it.message)
        }
        return decoded.getOrNull()
    }

    private suspend fun write(cacheKey: String, value: String, ttlSeconds: Long): Boolean =
        guarded("set_failed", cacheKey) { cache.set(cacheKey, value, ttlSeconds) } != null

    private suspend fun <V> guarded(event: String, cacheKey: String, block: suspend () -> V): V? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        logger.warn("cache event={} area={} key={} error={}", event, area.name, cacheKey, error.message)
        null
    }

    private companion object {
        const val GOOD_SUFFIX = ":good"
        const val BACKOFF_SUFFIX = ":backoff"
        const val GOOD_TTL_SECONDS = 86_400L
        val logger = LoggerFactory.getLogger(PublicExtractionCache::class.java)
    }
}
