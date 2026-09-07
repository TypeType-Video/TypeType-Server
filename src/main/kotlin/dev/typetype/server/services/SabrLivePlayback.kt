package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal data class SabrLivePlaybackSnapshot(
    val active: Boolean,
    val postLiveDvr: Boolean,
    val headSequence: Long,
    val headTimeMs: Long,
    val seekableStartMs: Long,
    val seekableEndMs: Long,
    val atLiveEdge: Boolean,
    val targetLatencyMs: Long,
)

internal fun SabrSessionHolder.livePlaybackSnapshot(): SabrLivePlaybackSnapshot? {
    val state = session.streamState
    val postLiveDvr = runCatching { state.isPostLiveDvr }.getOrDefault(false)
    val sessionLive = runCatching { session.isLive }.getOrDefault(false)
    val stateLive = runCatching { state.isLive }.getOrDefault(false)
    val detectedLive = expectsLive() || sessionLive || stateLive || postLiveDvr
    if (!detectedLive) return null

    val observedEndMs = maxOf(
        state.observedEndMs(audioFormat),
        state.observedEndMs(videoFormat),
        state.getBufferedEndMs(audioFormat),
        state.getBufferedEndMs(videoFormat),
        state.getMinBufferedEndMs(),
        0L,
    )
    val reportedHeadTimeMs = runCatching { state.liveHeadTimeMs }.getOrDefault(0L)
    val headTimeMs = reportedHeadTimeMs.takeIf { it > 0L } ?: observedEndMs
    val active = !postLiveDvr && (expectsLive() || sessionLive || stateLive)
    val seekableEndMs = if (active) headTimeMs else observedEndMs
    val seekableStartMs = if (active) {
        (seekableEndMs - LIVE_DVR_WINDOW_MS).coerceAtLeast(0L)
    } else {
        0L
    }
    val extractorAtLiveEdge = runCatching { session.isAtLiveEdge }.getOrDefault(false)
    val readerAtLiveEdge = headTimeMs - maxOf(playerTimeMs(), readerHeadMs()) <= LIVE_EDGE_TOLERANCE_MS
    return SabrLivePlaybackSnapshot(
        active = active,
        postLiveDvr = postLiveDvr,
        headSequence = maxOf(
            runCatching { session.liveHeadSequenceNumber }.getOrDefault(0L),
            runCatching { state.liveHeadSequenceNumber }.getOrDefault(0L),
            0L,
        ),
        headTimeMs = headTimeMs,
        seekableStartMs = seekableStartMs,
        seekableEndMs = seekableEndMs,
        atLiveEdge = active && (extractorAtLiveEdge || readerAtLiveEdge),
        targetLatencyMs = LIVE_TARGET_LATENCY_MS,
    )
}

internal fun SabrSessionHolder.resolvePlaybackStartMs(requestedStartMs: Long): Long {
    val requested = requestedStartMs.coerceAtLeast(0L)
    val live = livePlaybackSnapshot() ?: return requested
    if (!live.active) return requested.coerceAtMost(live.seekableEndMs.takeIf { it > 0L } ?: requested)
    if (requested > 0L) return requested.coerceIn(live.seekableStartMs, live.seekableEndMs)
    val targetStartMs = (live.seekableEndMs - live.targetLatencyMs).coerceAtLeast(live.seekableStartMs)
    return maxOf(targetStartMs, availableLiveMediaStartMs() ?: targetStartMs)
}

private fun SabrSessionHolder.availableLiveMediaStartMs(): Long? {
    val audioStartMs = earliestObservedMediaStartMs(audioFormat) ?: return null
    if (!isVideoActive()) return audioStartMs
    val videoStartMs = earliestObservedMediaStartMs(videoFormat) ?: return null
    return maxOf(audioStartMs, videoStartMs)
}

internal fun SabrSessionHolder.isFutureLiveRequest(request: SabrSegmentRequest): Boolean {
    if (request.isInitializationSegment) return false
    val live = livePlaybackSnapshot()?.takeIf { it.active } ?: return false
    if (session.getCachedSegment(request) != null) return false
    if (request.format.itag == videoFormat.itag &&
        live.headSequence > 0L &&
        request.sequenceNumber.toLong() < live.headSequence
    ) return false
    if (observedMediaSegment(request.format) != null &&
        playbackSegmentEndMs(request.format, request.sequenceNumber) <
        live.headTimeMs - LIVE_HISTORICAL_REQUEST_TOLERANCE_MS
    ) return false
    if (session.getReadableSegment(request) != null && !isHistoricalLiveRequest(request)) return true
    val state = session.streamState
    observedMediaSegment(request.format)?.let { observed ->
        val distanceFromComplete = request.sequenceNumber.toLong() - observed.header.sequenceNumber.toLong()
        if (distanceFromComplete < 0L) return false
        if (distanceFromComplete == 0L) return true
        if (distanceFromComplete <= LIVE_FUTURE_SEGMENT_TOLERANCE && !isHistoricalLiveRequest(request)) return true
    }
    val trackHeadSequence = runCatching { state.getMaxSegment(request.format) }.getOrDefault(0)
    if (trackHeadSequence <= 0) return false
    if (request.sequenceNumber < trackHeadSequence) return false
    if (request.sequenceNumber > trackHeadSequence) {
        return request.sequenceNumber <= trackHeadSequence + LIVE_FUTURE_SEGMENT_TOLERANCE &&
            !isHistoricalLiveRequest(request)
    }
    val requestStartMs = playbackSegmentStartMs(request.format, request.sequenceNumber)
    val completeEndMs = runCatching { state.getBufferedEndMs(request.format) }.getOrDefault(0L)
    return requestStartMs > 0L && requestStartMs >= completeEndMs && !isHistoricalLiveRequest(request)
}

internal fun SabrSessionHolder.isHistoricalLiveRequest(request: SabrSegmentRequest): Boolean {
    if (request.isInitializationSegment) return false
    val live = livePlaybackSnapshot()?.takeIf { it.active } ?: return false
    val observed = observedMediaSegment(request.format) ?: return false
    val requestEndMs = playbackSegmentEndMs(request.format, request.sequenceNumber)
    if (requestEndMs < live.headTimeMs - LIVE_HISTORICAL_REQUEST_TOLERANCE_MS) return true
    lastServedSequence(request.format)?.let { lastServed ->
        if (request.sequenceNumber in lastServed..lastServed + LIVE_FUTURE_SEGMENT_TOLERANCE) return false
    }
    return request.sequenceNumber < observed.header.sequenceNumber
}

internal fun SabrSessionHolder.liveRetryAfterMs(blockedRequests: List<SabrSegmentRequest> = emptyList()): Long =
    DEFAULT_PLAYBACK_RETRY_MS

private fun dev.typetype.server.sabr.YoutubeSabrStreamState.observedEndMs(
    format: YoutubeSabrFormat,
): Long {
    val sequence = runCatching { getMaxSegment(format) }.getOrDefault(0)
    return if (sequence > 0) runCatching { getSegmentEndMs(format, sequence) }.getOrDefault(0L).coerceAtLeast(0L) else 0L
}

internal const val LIVE_EDGE_POLL_MS = 2_000L
internal const val DEFAULT_PLAYBACK_RETRY_MS = 500L
private const val LIVE_TARGET_LATENCY_MS = 20_000L
private const val LIVE_EDGE_TOLERANCE_MS = 15_000L
private const val LIVE_HISTORICAL_REQUEST_TOLERANCE_MS = LIVE_TARGET_LATENCY_MS + LIVE_EDGE_TOLERANCE_MS
private const val LIVE_DVR_WINDOW_MS = 12L * 60L * 60L * 1_000L
internal const val LIVE_FUTURE_SEGMENT_TOLERANCE = 2
