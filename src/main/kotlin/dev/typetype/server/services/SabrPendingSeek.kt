package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest

internal fun SabrSessionHolder.consumeMatchingSeek(request: SabrSegmentRequest): Boolean {
    pendingRefetchRequest()?.takeIf { it.matches(request) }?.let {
        consumeRefetch()
        setPlaybackState(SabrPlaybackState.REPOSITIONING)
        prepareForExplicitRewind(request)
        return true
    }
    pendingForwardSeekRequest()?.takeIf { it.matches(request) }?.let {
        consumeForwardSeek()
        setPlaybackState(SabrPlaybackState.REPOSITIONING)
        prepareForExplicitForwardJump(request)
        return true
    }
    return false
}

internal fun SabrSessionHolder.prepareForExplicitRewind(request: SabrSegmentRequest): Unit =
    session.prepareForRewind(request, explicitSeekPositionMs())

internal fun SabrSessionHolder.prepareForExplicitForwardJump(request: SabrSegmentRequest): Unit =
    session.prepareForForwardJump(request, explicitSeekPositionMs())

internal fun SabrSessionHolder.prepareForHistoricalLiveRewind(request: SabrSegmentRequest): Unit {
    val seekPositionMs = requestedSeekTimeMs()?.takeIf { request.contains(this, it) }
    if (seekPositionMs == null) session.prepareForRewind(request) else session.prepareForRewind(request, seekPositionMs)
}

private fun SabrSessionHolder.explicitSeekPositionMs(): Long = requestedSeekTimeMs() ?: playerTimeMs()

private fun SabrSegmentRequest.contains(holder: SabrSessionHolder, positionMs: Long): Boolean {
    val startMs = holder.playbackSegmentStartMs(format, sequenceNumber)
    val endMs = holder.playbackSegmentEndMs(format, sequenceNumber)
    return positionMs >= startMs && (endMs <= startMs || positionMs < endMs)
}

private fun SabrSegmentRequest.matches(other: SabrSegmentRequest): Boolean =
    format.itag == other.format.itag && sequenceNumber == other.sequenceNumber &&
        isInitializationSegment == other.isInitializationSegment
