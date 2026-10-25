package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import kotlinx.serialization.KSerializer
import org.slf4j.LoggerFactory

object PublicExtractionCache {
    private val logger = LoggerFactory.getLogger(PublicExtractionCache::class.java)
    private val flights = SingleFlight<ExtractionResult<*>>()

    suspend fun <T> getOrLoad(
        cache: CacheService,
        area: String,
        key: String,
        serializer: KSerializer<T>,
        ttlSeconds: (T) -> Long,
        load: suspend () -> ExtractionResult<T>,
    ): ExtractionResult<T> = getOrLoad(
        cache = cache,
        area = PublicCacheArea(area, serializer, keepLastGood = false, backoffOnFailure = false) {
            CacheAdmission.Valid(ttlSeconds(it))
        },
        key = key,
        load = load,
    )

    suspend fun <T> getOrLoad(
        cache: CacheService,
        area: PublicCacheArea<T>,
        key: String,
        clock: () -> Long = System::currentTimeMillis,
        load: suspend () -> ExtractionResult<T>,
    ): ExtractionResult<T> {
        val store = PublicCacheStore(cache, area, key)
        store.primary()?.let {
            logger.debug("cache event=hit area={} key={}", area.name, key)
            return ExtractionResult.Success(it)
        }
        @Suppress("UNCHECKED_CAST")
        return flights.run(key) { loadThrough(store, area, key, clock, load) } as ExtractionResult<T>
    }

    private suspend fun <T> loadThrough(
        store: PublicCacheStore<T>,
        area: PublicCacheArea<T>,
        key: String,
        clock: () -> Long,
        load: suspend () -> ExtractionResult<T>,
    ): ExtractionResult<T> {
        store.primary()?.let { return ExtractionResult.Success(it) }
        val backoff = store.backoff()
        if (backoff != null && backoff.isActive(clock())) {
            logger.debug("cache event=backoff_hit area={} key={}", area.name, key)
            store.lastGood()?.let { return ExtractionResult.Success(it) }
            backoff.failure()?.let { return it }
        }
        logger.debug("cache event=miss area={} key={}", area.name, key)
        return when (val result = load()) {
            is ExtractionResult.Success -> admit(store, area, key, clock, backoff, result.data)
            is ExtractionResult.Failure -> fallBack(store, area, key, clock, backoff, result, result.message)
            is ExtractionResult.BadRequest -> result
        }
    }

    private suspend fun <T> admit(
        store: PublicCacheStore<T>,
        area: PublicCacheArea<T>,
        key: String,
        clock: () -> Long,
        backoff: PublicCacheBackoff?,
        value: T,
    ): ExtractionResult<T> = when (val admission = area.admission(value)) {
        is CacheAdmission.Valid -> {
            store.storePrimary(value, admission.ttlSeconds)
            if (admission.ttlSeconds > 0L) store.storeLastGood(value, clock())
            if (backoff != null) store.clearBackoff()
            ExtractionResult.Success(value)
        }
        is CacheAdmission.Suspect -> {
            val good = store.lastGood()
            if (good != null) {
                startBackoff(store, area, key, clock, backoff, null, "suspect")
                ExtractionResult.Success(good)
            } else {
                store.storePrimary(value, admission.boundedTtlSeconds)
                ExtractionResult.Success(value)
            }
        }
        is CacheAdmission.Reject -> fallBack(
            store, area, key, clock, backoff,
            ExtractionResult.Failure(admission.reason, REJECTED_CODE), admission.reason,
        )
    }

    private suspend fun <T> fallBack(
        store: PublicCacheStore<T>,
        area: PublicCacheArea<T>,
        key: String,
        clock: () -> Long,
        backoff: PublicCacheBackoff?,
        failure: ExtractionResult.Failure,
        reason: String,
    ): ExtractionResult<T> {
        startBackoff(store, area, key, clock, backoff, failure, reason)
        return store.lastGood()?.let { ExtractionResult.Success(it) } ?: failure
    }

    private suspend fun <T> startBackoff(
        store: PublicCacheStore<T>,
        area: PublicCacheArea<T>,
        key: String,
        clock: () -> Long,
        backoff: PublicCacheBackoff?,
        failure: ExtractionResult.Failure?,
        reason: String,
    ) {
        if (!area.backoffOnFailure) return
        val now = clock()
        val next = (backoff ?: PublicCacheBackoff.NONE).next(now, failure)
        store.storeBackoff(next)
        logger.info(
            "cache event=backoff area={} key={} failures={} retryInSeconds={} reason={}",
            area.name, key, next.failures, (next.retryAt - now) / 1_000L, reason,
        )
    }

    const val REJECTED_CODE = "cache_rejected"
}
