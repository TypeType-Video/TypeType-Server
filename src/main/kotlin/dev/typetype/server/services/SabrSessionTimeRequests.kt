package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.mediaRequestsAt(playerTimeMs: Long): List<SabrSegmentRequest> =
    mediaRequestsAt(playerTimeMs, activeGeneration())

internal fun SabrSessionHolder.repositionTargets(
    targets: List<SabrSegmentRequest>,
    playerTimeMs: Long,
    generation: Long,
): List<SabrSegmentRequest> = targets.filter { request ->
    val cached = session.getCachedSegment(request)
    val warmedStartMs = adjacentWarmedLiveMediaStartMs(request, playerTimeMs)
    val targetPositionMs = when {
        cached != null -> playbackSegmentEndMs(request.format, request.sequenceNumber)
        warmedStartMs != null -> warmedStartMs
        else -> playbackSegmentStartMs(request.format, request.sequenceNumber)
    }
    setReaderPosition(request.format, targetPositionMs, generation)
    cached == null && warmedStartMs == null
}

private fun SabrSessionHolder.adjacentWarmedLiveMediaStartMs(
    request: SabrSegmentRequest,
    playerTimeMs: Long,
): Long? {
    if (livePlaybackSnapshot()?.active != true) return null
    val observed = observedMediaSegment(request.format) ?: return null
    if (observed.header.sequenceNumber != request.sequenceNumber + 1) return null
    val observedStartMs = observed.header.startMs.takeIf { it >= 0L } ?: return null
    val observedRequest = SabrSegmentRequest.media(request.format, observed.header.sequenceNumber)
    if (session.getCachedSegment(observedRequest) == null) return null
    val boundaryLeadMs = playbackSegmentDurationMs(request.format, request.sequenceNumber) +
        LIVE_TRACK_BOUNDARY_DRIFT_MS
    return observedStartMs.takeIf {
        it - playerTimeMs.coerceAtLeast(0L) in 0L..boundaryLeadMs
    }
}

internal fun SabrSessionHolder.mediaRequestsAt(playerTimeMs: Long, generation: Long): List<SabrSegmentRequest> = buildList {
    if (isVideoActive() && needsMediaAt(videoFormat, playerTimeMs, generation)) add(mediaRequestAt(videoFormat, playerTimeMs, generation))
    if (isAudioActive() && needsMediaAt(audioFormat, playerTimeMs, generation)) add(mediaRequestAt(audioFormat, playerTimeMs, generation))
}

private fun SabrSessionHolder.needsMediaAt(format: YoutubeSabrFormat, playerTimeMs: Long, generation: Long): Boolean =
    (readerPosition(format, generation) ?: return true) <= playerTimeMs.coerceAtLeast(0L)

private fun SabrSessionHolder.mediaRequestAt(
    format: YoutubeSabrFormat,
    playerTimeMs: Long,
    generation: Long,
): SabrSegmentRequest {
    val timeSequence = playbackStartSequence(format, playerTimeMs)
    val sequence = lastServedSequence(format, generation)?.let { last ->
        if (timeSequence <= last) last + 1 else timeSequence
    } ?: timeSequence
    return SabrSegmentRequest.media(format, sequence)
}

private const val LIVE_TRACK_BOUNDARY_DRIFT_MS = 500L
