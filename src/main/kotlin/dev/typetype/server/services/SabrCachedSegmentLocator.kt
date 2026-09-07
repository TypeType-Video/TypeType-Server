package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrSession

internal fun YoutubeSabrSession.findCachedMediaAt(
    format: YoutubeSabrFormat,
    targetMs: Long,
    predictedSequence: Int,
    fallbackDurationMs: Long? = null,
    allowFollowing: Boolean = false,
): SabrMediaSegment? {
    for (distance in 1..MAX_SEQUENCE_DISTANCE) {
        cachedMedia(format, predictedSequence + distance)
            ?.takeIf { it.covers(targetMs, fallbackDurationMs) }
            ?.let { return it }
        cachedMedia(format, predictedSequence - distance)
            ?.takeIf { it.covers(targetMs, fallbackDurationMs) }
            ?.let { return it }
    }
    if (!allowFollowing) return null
    for (distance in 1..MAX_SEQUENCE_DISTANCE) {
        cachedMedia(format, predictedSequence + distance)
            ?.takeIf { it.startsWithinFollowingRange(targetMs, fallbackDurationMs, distance) }
            ?.let { return it }
    }
    return null
}

private fun YoutubeSabrSession.cachedMedia(format: YoutubeSabrFormat, sequence: Int): SabrMediaSegment? {
    if (sequence < 1) return null
    return getCachedSegment(SabrSegmentRequest.media(format, sequence))
}

private fun SabrMediaSegment.covers(targetMs: Long, fallbackDurationMs: Long?): Boolean {
    val startMs = header.startMs
    val durationMs = header.durationMs.takeIf { it > 0L } ?: fallbackDurationMs ?: return false
    return startMs >= 0L &&
        targetMs >= startMs - TIMING_TOLERANCE_MS &&
        targetMs < startMs + durationMs
}

private fun SabrMediaSegment.startsWithinFollowingRange(
    targetMs: Long,
    fallbackDurationMs: Long?,
    sequenceDistance: Int,
): Boolean {
    val durationMs = header.durationMs.takeIf { it > 0L } ?: fallbackDurationMs ?: return false
    val leadMs = header.startMs - targetMs
    val maximumLeadMs = maximumFollowingLeadMs(durationMs, sequenceDistance)
    return header.startMs >= 0L && leadMs in -TIMING_TOLERANCE_MS..maximumLeadMs
}

internal fun maximumFollowingLeadMs(durationMs: Long, sequenceDistance: Int): Long {
    val distance = sequenceDistance.coerceAtLeast(1).toLong()
    val toleranceMs = LIVE_FOLLOWING_TIMING_TOLERANCE_MS
    val boundedDurationMs = durationMs.coerceAtMost((Long.MAX_VALUE - toleranceMs) / distance)
    return boundedDurationMs * distance + toleranceMs
}

private const val MAX_SEQUENCE_DISTANCE = 24
internal const val TIMING_TOLERANCE_MS = 2L
internal const val LIVE_FOLLOWING_TIMING_TOLERANCE_MS = 250L
