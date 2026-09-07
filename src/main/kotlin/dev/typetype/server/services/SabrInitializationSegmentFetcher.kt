package dev.typetype.server.services

import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import org.slf4j.LoggerFactory

internal suspend fun fetchSabrInitializationSegment(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    localization: Localization,
    segmentCache: SabrSegmentCache?,
    markServed: Boolean,
): SabrMediaSegment? = holder.pumpMutex.withLock {
    holder.session.getCachedSegment(request)?.let { segment ->
        if (markServed) holder.markServed(segment)
        segmentCache?.put(holder, segment)
        return@withLock segment
    }
    if (holder.session.isBeyondEnd(request)) return@withLock null
    val result = runCatchingNonCancellation {
        holder.session.prepareForInitialization(request.format)
        holder.withPlayerContext { fetchSegment(request, localization) }
    }
    result.onFailure { error ->
        logger.warn(
            "sabr_init event=fetch_failed videoId={} itag={} errorType={} error={}",
            holder.key.videoId,
            request.format.itag,
            error.javaClass.simpleName,
            error.message,
            error,
        )
    }
    result
        .getOrNull()
        ?.also {
            if (markServed) holder.markServed(it)
            segmentCache?.put(holder, it)
        }
}

private val logger = LoggerFactory.getLogger("SabrInitializationSegmentFetcher")
