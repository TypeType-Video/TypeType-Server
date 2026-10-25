package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
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
    unauthorizedRecovery: SabrUnauthorizedResponseRecovery,
): SabrMediaSegment? = holder.pumpMutex.withLock {
    holder.session.getCachedSegment(request)?.let { segment ->
        if (markServed) holder.markServed(segment)
        segmentCache?.put(holder, segment)
        return@withLock segment
    }
    if (holder.session.isBeyondEnd(request) || holder.playbackState() == SabrPlaybackState.TERMINAL) return@withLock null
    val requestNumber = holder.session.requestNumber
    val result = runCatchingNonCancellation {
        holder.withPlayerContextOffThread {
            prepareForInitialization(request.format)
            fetchSegment(request, localization)
        }
    }
    val status = SabrUnauthorizedResponseRecovery.latestUnauthorizedStatus(holder.session.diagnosticTrace)
    val recovered = if (
        holder.session.requestNumber > requestNumber &&
        status != null &&
        (result.isFailure || result.getOrNull() == null)
    ) {
        retryUnauthorized(holder, request, localization, unauthorizedRecovery, status)
    } else {
        result
    }
    recovered.onFailure { error ->
        if (error is SabrUpstreamUnauthorizedException) unauthorizedRecovery.fail(holder, error)
        initFetcherLogger.warn(
            "sabr_init_trace videoId={} trace={}",
            holder.key.videoId,
            holder.session.diagnosticTrace.takeLast(700),
        )
        initFetcherLogger.warn(
            "sabr_init event=fetch_failed videoId={} itag={} errorType={} error={}",
            holder.key.videoId,
            request.format.itag,
            error.javaClass.simpleName,
            error.message,
            error,
        )
    }
    recovered
        .getOrNull()
        ?.also {
            if (markServed) holder.markServed(it)
            segmentCache?.put(holder, it)
        }
}

private suspend fun retryUnauthorized(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    localization: Localization,
    unauthorizedRecovery: SabrUnauthorizedResponseRecovery,
    status: Int,
): Result<SabrMediaSegment?> {
    initFetcherLogger.info(
        "sabr_init event=unauthorized_retry videoId={} itag={} httpStatus={}",
        holder.key.videoId,
        request.format.itag,
        status,
    )
    val retry = runCatchingNonCancellation {
        runInterruptible(Dispatchers.IO) { unauthorizedRecovery.verify(holder) }
        val requestNumber = holder.session.requestNumber
        val segment = runCatchingNonCancellation {
            holder.withPlayerContextOffThread {
                prepareForInitialization(request.format)
                fetchSegment(request, localization)
            }
        }
        val stillRefused = SabrUnauthorizedResponseRecovery.latestUnauthorizedStatus(holder.session.diagnosticTrace)
            ?.takeIf { holder.session.requestNumber > requestNumber }
        if (stillRefused != null && segment.getOrNull() == null) throw SabrUpstreamUnauthorizedException(stillRefused)
        segment.getOrThrow()
    }
    initFetcherLogger.info(
        "sabr_init event=unauthorized_retry_complete videoId={} itag={} result={}",
        holder.key.videoId,
        request.format.itag,
        if (retry.getOrNull() == null) "miss" else "hit",
    )
    return retry
}

private val initFetcherLogger = LoggerFactory.getLogger("SabrInitializationSegmentFetcher")
