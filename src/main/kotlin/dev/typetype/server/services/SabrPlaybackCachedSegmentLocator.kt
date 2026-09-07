package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal suspend fun SabrSessionStore.findCachedPlaybackMediaAt(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    targetMs: Long,
    predictedSequence: Int,
    allowFollowing: Boolean = false,
): CachedSabrSegment? {
    for (distance in 0..MAX_SEQUENCE_DISTANCE) {
        cachedMedia(holder, format, predictedSequence + distance)?.let {
            if (it.coversPlaybackTime(holder, format, targetMs)) return it
        }
        if (distance > 0) {
            cachedMedia(holder, format, predictedSequence - distance)?.let {
                if (it.coversPlaybackTime(holder, format, targetMs)) return it
            }
        }
    }
    if (!allowFollowing) return null
    val firstSequence = (predictedSequence.toLong() - MAX_SEQUENCE_DISTANCE).coerceAtLeast(1L).toInt()
    val lastSequence = (predictedSequence.toLong() + MAX_SEQUENCE_DISTANCE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    for (sequence in firstSequence..lastSequence) {
        cachedMedia(holder, format, sequence)?.let {
            val distance = kotlin.math.abs(sequence.toLong() - predictedSequence).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (it.startsWithinFollowingRange(holder, format, targetMs, distance)) return it
        }
    }
    return null
}

private suspend fun SabrSessionStore.cachedMedia(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    sequence: Int,
): CachedSabrSegment? {
    if (sequence < 1) return null
    return cachedSegment(holder, SabrSegmentRequest.media(format, sequence))
}

internal fun CachedSabrSegment.coversPlaybackTime(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    targetMs: Long,
): Boolean {
    val effectiveStartMs = startMs.takeIf { it >= 0L } ?: holder.playbackSegmentStartMs(format, sequence)
    val effectiveDurationMs = durationMs.takeIf { it > 0L } ?: holder.playbackSegmentDurationMs(format, sequence)
    return targetMs >= effectiveStartMs - TIMING_TOLERANCE_MS && targetMs < effectiveStartMs + effectiveDurationMs
}

internal fun CachedSabrSegment.isAcceptableLiveFollowingSegment(
    expectedSequence: Int,
    targetMs: Long,
    continuesServedTrack: Boolean,
): Boolean = !continuesServedTrack ||
    sequence <= expectedSequence + LIVE_CONTINUATION_SEQUENCE_GAP ||
    startMs > targetMs + MAX_RECOVERABLE_LIVE_GAP_MS

private fun CachedSabrSegment.startsWithinFollowingRange(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    targetMs: Long,
    sequenceDistance: Int,
): Boolean {
    val effectiveStartMs = startMs.takeIf { it >= 0L } ?: holder.playbackSegmentStartMs(format, sequence)
    val effectiveDurationMs = durationMs.takeIf { it > 0L } ?: holder.playbackSegmentDurationMs(format, sequence)
    val leadMs = effectiveStartMs - targetMs
    return leadMs in -TIMING_TOLERANCE_MS..maximumFollowingLeadMs(effectiveDurationMs, sequenceDistance)
}

private const val MAX_SEQUENCE_DISTANCE = 24
private const val LIVE_CONTINUATION_SEQUENCE_GAP = 1
