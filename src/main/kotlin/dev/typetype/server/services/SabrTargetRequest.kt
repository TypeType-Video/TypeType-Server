package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrSession
import org.slf4j.LoggerFactory

internal fun YoutubeSabrSession.fetchTargetedSegment(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    localization: Localization,
    playerTimeMs: Long? = null,
): SabrMediaSegment? {
    val targetPlayerTimeMs = when {
        playerTimeMs != null -> playerTimeMs
        request.isInitializationSegment -> 0L
        else -> holder.targetTimeInsideSegment(request)
    }
    val result = runCatchingNonCancellation {
        prepareForTarget(request, targetPlayerTimeMs)
        pumpOnceStreamingUntilCached(localization, request)
        getCachedSegment(request)
    }
    result.onFailure { error ->
        SabrPlaybackDiagnostics.record(holder, request, error.message)
        logger.warn(
            "sabr_target event=fetch_failed videoId={} itag={} seq={} init={} targetMs={} errorType={} error={}",
            holder.key.videoId,
            request.format.itag,
            request.sequenceNumber,
            request.isInitializationSegment,
            targetPlayerTimeMs,
            error.javaClass.simpleName,
            error.message,
            error,
        )
    }
    return result.getOrNull()
        ?.takeIf { it.matches(request) }
        ?.also { SabrPlaybackDiagnostics.clear(holder, it) }
}

private fun YoutubeSabrSession.prepareForTarget(request: SabrSegmentRequest, targetPlayerTimeMs: Long): Unit {
    if (request.isInitializationSegment) return
    streamState.setPlayerTimeMs(targetPlayerTimeMs.coerceAtLeast(0L))
}

private fun SabrMediaSegment.matches(request: SabrSegmentRequest): Boolean {
    if (header.itag != request.format.itag) return false
    return if (request.isInitializationSegment) {
        header.isInitSegment
    } else {
        !header.isInitSegment && header.sequenceNumber == request.sequenceNumber
    }
}

private fun SabrSessionHolder.targetTimeInsideSegment(request: SabrSegmentRequest): Long {
    val startMs = playbackSegmentStartMs(request.format, request.sequenceNumber)
    val nextStartMs = playbackSegmentStartMs(request.format, request.sequenceNumber + 1)
    if (nextStartMs <= startMs + 1L) return startMs
    return minOf(startMs + TARGET_SEGMENT_OFFSET_MS, nextStartMs - 1L)
}

private val logger = LoggerFactory.getLogger("SabrTargetRequest")

private const val TARGET_SEGMENT_OFFSET_MS = 1_000L
