package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.playbackStartSequence(format: YoutubeSabrFormat, playerTimeMs: Long): Int {
    liveSequenceAt(format, playerTimeMs)?.let { return it }
    return session.streamState.getSegmentNumberAtOrAfterTimeMs(format, playerTimeMs.coerceAtLeast(0L))
        .coerceAtLeast(1)
}

internal fun SabrSessionHolder.playbackContinuationSequence(
    format: YoutubeSabrFormat,
    playerTimeMs: Long,
    continueAfterLastServed: Boolean,
): Int = lastServedSequence(format)
    ?.takeIf { continueAfterLastServed && it < Int.MAX_VALUE }
    ?.plus(1)
    ?: playbackStartSequence(format, playerTimeMs)

internal fun SabrSessionHolder.playbackSegmentStartMs(format: YoutubeSabrFormat, sequence: Int): Long {
    val observed = observedMediaSegment(format)
    val observedStartMs = observed?.header?.startMs?.takeIf { it >= 0L }
    if (observed != null && observedStartMs != null) {
        val durationMs = playbackSegmentDurationMs(format, sequence)
        val sequenceDelta = sequence.toLong() - observed.header.sequenceNumber
        return (observedStartMs + sequenceDelta * durationMs).coerceAtLeast(0L)
    }
    return session.streamState.getSegmentStartMs(format, sequence).coerceAtLeast(0L)
}

internal fun SabrSessionHolder.playbackSegmentDurationMs(format: YoutubeSabrFormat, sequence: Int): Long {
    val observed = observedMediaSegment(format)
    val observedStartMs = observed?.header?.startMs?.takeIf { it >= 0L }
    val observedDurationMs = observed?.header?.durationMs?.takeIf { it > 0L }
    if (observedDurationMs != null) return observedDurationMs
    if (observed != null && observedStartMs != null) {
        livePlaybackSnapshot()
            ?.takeIf { it.active }
            ?.segmentDurationFrom(observed.header.sequenceNumber, observedStartMs)
            ?.takeIf { it in MIN_LIVE_SEGMENT_DURATION_MS..MAX_LIVE_SEGMENT_DURATION_MS }
            ?.let { return it }
    }
    val startMs = session.streamState.getSegmentStartMs(format, sequence).coerceAtLeast(0L)
    return (session.streamState.getSegmentEndMs(format, sequence) - startMs).coerceAtLeast(1L)
}

internal fun SabrSessionHolder.playbackSegmentEndMs(format: YoutubeSabrFormat, sequence: Int): Long =
    playbackSegmentStartMs(format, sequence) + playbackSegmentDurationMs(format, sequence)

private fun SabrSessionHolder.liveSequenceAt(format: YoutubeSabrFormat, playerTimeMs: Long): Int? {
    val live = livePlaybackSnapshot()?.takeIf { it.active } ?: return null
    val observed = observedMediaSegment(format) ?: return null
    val observedStartMs = observed.header.startMs.takeIf { it >= 0L }
        ?: return null
    val segmentDurationMs = playbackSegmentDurationMs(format, observed.header.sequenceNumber)
    if (segmentDurationMs !in MIN_LIVE_SEGMENT_DURATION_MS..MAX_LIVE_SEGMENT_DURATION_MS) return null
    val offset = Math.floorDiv(playerTimeMs.coerceAtLeast(0L) - observedStartMs, segmentDurationMs)
    val sequence = (observed.header.sequenceNumber.toLong() + offset)
        .coerceIn(1L, Int.MAX_VALUE.toLong())
        .toInt()
    val liveHead = live.headSequence.takeIf { it > 0L } ?: return sequence
    val trackHead = observed.header.sequenceNumber
    val distanceBehindLiveHead = liveHead - trackHead.toLong()
    val effectiveHead = trackHead.takeIf {
        it > 0 && distanceBehindLiveHead in 0L..LIVE_FUTURE_SEGMENT_TOLERANCE.toLong()
    } ?: liveHead.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return sequence.coerceAtMost(effectiveHead)
}

private fun SabrLivePlaybackSnapshot.segmentDurationFrom(observedSequence: Int, observedStartMs: Long): Long? {
    val sequenceDelta = headSequence - observedSequence
    val timeDeltaMs = headTimeMs - observedStartMs
    if (sequenceDelta <= 0L || timeDeltaMs <= 0L) return null
    return Math.floorDiv(timeDeltaMs + sequenceDelta / 2L, sequenceDelta).takeIf { it > 0L }
}

private const val MIN_LIVE_SEGMENT_DURATION_MS = 100L
private const val MAX_LIVE_SEGMENT_DURATION_MS = 30_000L
