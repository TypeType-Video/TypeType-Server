package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal suspend fun pumpLiveReadAhead(
    holder: SabrSessionHolder,
    runtime: SabrPumpRuntime,
    pump: suspend () -> Int,
    onResolved: (SabrSessionHolder, SabrMediaSegment) -> Unit,
): Boolean {
    val targetAheadMs = runtime.targetReadaheadCushionMs(holder)
    if (holder.observedLiveAheadMs() >= targetAheadMs) {
        holder.setPlaybackState(SabrPlaybackState.IDLE)
        return false
    }
    holder.setPlaybackState(SabrPlaybackState.REQUESTING)
    withLiveContinuationRequestShape(holder) { pump() }
    val cached = holder.cacheObservedLiveContinuation(onResolved)
    val waitingForLive = cached == 0 && holder.nextSegmentDemand()?.let(holder::isFutureLiveRequest) == true
    holder.setPlaybackState(if (waitingForLive) SabrPlaybackState.WAITING_FOR_LIVE else SabrPlaybackState.IDLE)
    return cached > 0 && holder.observedLiveAheadMs() < targetAheadMs
}

fun SabrSessionHolder.observedLiveAheadMs(): Long {
    val audioEndMs = observedMediaEndMs(audioFormat) ?: return 0L
    val videoEndMs = if (isVideoActive()) observedMediaEndMs(videoFormat) ?: return 0L else Long.MAX_VALUE
    return (minOf(audioEndMs, videoEndMs) - playerTimeMs()).coerceAtLeast(0L)
}

private fun SabrSessionHolder.observedMediaEndMs(format: YoutubeSabrFormat): Long? {
    val header = observedMediaSegment(format)?.header ?: return null
    val startMs = header.startMs.takeIf { it >= 0L } ?: return null
    val durationMs = header.durationMs.takeIf { it > 0L }
        ?: playbackSegmentDurationMs(format, header.sequenceNumber)
    return startMs + durationMs
}

private fun SabrSessionHolder.cacheObservedLiveContinuation(
    onResolved: (SabrSessionHolder, SabrMediaSegment) -> Unit,
): Int = listOfNotNull(
    audioFormat.takeIf { isAudioActive() },
    videoFormat.takeIf { isVideoActive() },
).sumOf { format ->
    val observedSequence = observedMediaSegment(format)?.header?.sequenceNumber ?: return@sumOf 0
    var cached = 0
    for (sequence in observedSequence + 1..observedSequence + MAX_CONTINUATION_SCAN) {
        val segment = session.getCachedSegment(SabrSegmentRequest.media(format, sequence)) ?: break
        observeMediaSegment(segment)
        onResolved(this, segment)
        cached++
    }
    cached
}

private const val MAX_CONTINUATION_SCAN = 24
