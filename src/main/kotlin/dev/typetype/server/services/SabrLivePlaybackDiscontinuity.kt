package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.isLiveDemandOutsideRecoverableWindow(request: SabrSegmentRequest): Boolean {
    if (request.isInitializationSegment) return false
    val live = livePlaybackSnapshot()?.takeIf { it.active } ?: return false
    val requestEndMs = playbackSegmentEndMs(request.format, request.sequenceNumber)
    return requestEndMs > 0L && live.headTimeMs - requestEndMs > MAX_RECOVERABLE_LIVE_GAP_MS
}

internal fun SabrSessionHolder.failLivePlaybackDiscontinuity(
    format: YoutubeSabrFormat,
    targetMs: Long,
    segment: CachedSabrSegment,
    hasBufferedMedia: Boolean,
): Boolean {
    if (!hasBufferedMedia || livePlaybackSnapshot()?.active != true) return false
    if (segment.startMs <= targetMs + MAX_RECOVERABLE_LIVE_GAP_MS) return false
    failTerminal(sabrRecoverableFailureMessage("live ${format.itag} media discontinuity"))
    return true
}

internal const val MAX_RECOVERABLE_LIVE_GAP_MS = 60_000L + LIVE_FOLLOWING_TIMING_TOLERANCE_MS
