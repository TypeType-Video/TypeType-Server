package dev.typetype.server.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

internal class YouTubeSubtitleDeliveryService(
    private val resolver: YouTubeSubtitleTrackResolver,
    private val fetcher: YouTubeSubtitleContentFetcher,
    private val cache: YouTubeSubtitleCache,
) {
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<YouTubeSubtitleContentResult>>()
    private val upstreamPermits = Semaphore(MAX_CONCURRENT_UPSTREAM_REQUESTS)

    suspend fun fetch(selection: YouTubeSubtitleSelection): YouTubeSubtitleContentResult {
        cache.get(selection)?.let { return it }
        val pending = CompletableDeferred<YouTubeSubtitleContentResult>()
        val existing = inFlight.putIfAbsent(selection.cacheKey, pending)
        if (existing != null) return existing.await()
        if (!upstreamPermits.tryAcquire()) {
            pending.complete(YouTubeSubtitleContentResult.Unavailable)
            inFlight.remove(selection.cacheKey, pending)
            return YouTubeSubtitleContentResult.Unavailable
        }
        return try {
            val result = cache.get(selection) ?: load(selection)
            if (result is YouTubeSubtitleContentResult.Ready) cache.put(selection, result)
            pending.complete(result)
            result
        } catch (error: Throwable) {
            if (error is CancellationException) {
                pending.cancel(error)
                throw error
            }
            pending.complete(YouTubeSubtitleContentResult.Unavailable)
            YouTubeSubtitleContentResult.Unavailable
        } finally {
            inFlight.remove(selection.cacheKey, pending)
            upstreamPermits.release()
        }
    }

    private suspend fun load(selection: YouTubeSubtitleSelection): YouTubeSubtitleContentResult {
        repeat(MAX_RESOLUTION_ATTEMPTS) { attempt ->
            when (val resolution = resolver.resolve(selection)) {
                YouTubeSubtitleResolution.NotFound -> return YouTubeSubtitleContentResult.NotFound
                YouTubeSubtitleResolution.Throttled -> return YouTubeSubtitleContentResult.Throttled
                YouTubeSubtitleResolution.Unavailable -> return YouTubeSubtitleContentResult.Unavailable
                is YouTubeSubtitleResolution.Ready -> {
                    val result = fetchResolved(resolution.track, selection.format)
                    if (result == YouTubeSubtitleFetchResult.Expired && attempt < MAX_RESOLUTION_ATTEMPTS - 1) {
                        return@repeat
                    }
                    return result.toContentResult(selection.format, resolution.track.isLive)
                }
            }
        }
        return YouTubeSubtitleContentResult.Expired
    }

    private suspend fun fetchResolved(
        track: ResolvedYouTubeSubtitle,
        format: YouTubeSubtitleFormat,
    ): YouTubeSubtitleFetchResult {
        if (track.isUrl) return fetcher.fetch(track.content, format)
        val bytes = track.content.encodeToByteArray()
        return if (isValidSubtitlePayload(bytes, format)) YouTubeSubtitleFetchResult.Ready(bytes)
        else YouTubeSubtitleFetchResult.InvalidPayload
    }

    private fun YouTubeSubtitleFetchResult.toContentResult(
        format: YouTubeSubtitleFormat,
        isLive: Boolean,
    ): YouTubeSubtitleContentResult = when (this) {
        is YouTubeSubtitleFetchResult.Ready -> YouTubeSubtitleContentResult.Ready(content, format, isLive)
        YouTubeSubtitleFetchResult.Expired -> YouTubeSubtitleContentResult.Expired
        YouTubeSubtitleFetchResult.Throttled -> YouTubeSubtitleContentResult.Throttled
        YouTubeSubtitleFetchResult.InvalidPayload -> YouTubeSubtitleContentResult.InvalidPayload
        YouTubeSubtitleFetchResult.Unavailable -> YouTubeSubtitleContentResult.Unavailable
    }

    private companion object {
        const val MAX_RESOLUTION_ATTEMPTS = 2
        const val MAX_CONCURRENT_UPSTREAM_REQUESTS = 64
    }
}
