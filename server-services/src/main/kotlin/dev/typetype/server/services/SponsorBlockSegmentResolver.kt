package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.SponsorBlockSegmentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SponsorBlockResolution(val segments: Array<SponsorBlockSegment>, val available: Boolean)

class SponsorBlockSegmentResolver(
    private val cache: CacheService,
    private val scope: CoroutineScope,
    private val waitMs: Long = WAIT_MS,
    private val fetchTimeoutMs: Long = FETCH_TIMEOUT_MS,
    private val cooldownMs: Long = FAILURE_COOLDOWN_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val unavailableUntil = AtomicLong(0L)
    private val inFlight = ConcurrentHashMap<String, Deferred<Array<SponsorBlockSegment>?>>()

    suspend fun resolve(
        videoId: String,
        fetch: suspend () -> Array<SponsorBlockSegment>,
    ): SponsorBlockResolution {
        val cacheKey = "sponsorblock:$videoId"
        cached(cacheKey)?.let { return SponsorBlockResolution(it, available = true) }
        if (now() < unavailableUntil.get()) return UNAVAILABLE
        val pending = inFlight.computeIfAbsent(videoId) {
            scope.async {
                try {
                    fetchAndCache(cacheKey, fetch)
                } finally {
                    inFlight.remove(videoId)
                }
            }
        }
        val segments = withTimeoutOrNull(waitMs) { pending.await() }
        if (segments == null) {
            if (pending.isActive) pause()
            return UNAVAILABLE
        }
        return SponsorBlockResolution(segments, available = true)
    }

    private suspend fun fetchAndCache(
        cacheKey: String,
        fetch: suspend () -> Array<SponsorBlockSegment>,
    ): Array<SponsorBlockSegment>? {
        val segments = runExtractionCatching { withTimeout(fetchTimeoutMs) { fetch() } }.getOrElse {
            pause()
            return null
        }
        unavailableUntil.set(0L)
        runCatching {
            cache.set(cacheKey, CacheJson.encodeToString(segments.map { it.toSegmentItem() }), CACHE_TTL_SECONDS)
        }
        return segments
    }

    private fun pause() = unavailableUntil.set(now() + cooldownMs)

    private suspend fun cached(cacheKey: String): Array<SponsorBlockSegment>? {
        val cached = runExtractionCatching { cache.get(cacheKey) }.getOrNull() ?: return null
        return runCatching {
            CacheJson.decodeFromString<List<SponsorBlockSegmentItem>>(cached).toSponsorBlockSegments()
        }.getOrNull()
    }

    private companion object {
        const val CACHE_TTL_SECONDS = 21600L
        const val WAIT_MS = 2_000L
        const val FETCH_TIMEOUT_MS = 15_000L
        const val FAILURE_COOLDOWN_MS = 60_000L
        val UNAVAILABLE = SponsorBlockResolution(emptyArray(), available = false)
    }
}
