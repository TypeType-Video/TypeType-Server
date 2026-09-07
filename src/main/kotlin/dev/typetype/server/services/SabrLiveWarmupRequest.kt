package dev.typetype.server.services

import dev.typetype.server.sabr.SabrBufferedRange
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.YoutubeSabrFormat

internal data class SabrLiveWarmupTarget(
    val sequence: Int,
    val timeMs: Long,
    val segmentDurationMs: Long,
)

internal fun SabrSessionHolder.liveWarmupTarget(): SabrLiveWarmupTarget? {
    val live = livePlaybackSnapshot()
        ?.takeIf { it.active && it.headSequence > 1L && it.headTimeMs > 0L }
        ?: return null
    val segmentDurationMs = (live.headTimeMs / live.headSequence)
        .coerceIn(MIN_LIVE_SEGMENT_DURATION_MS, MAX_LIVE_SEGMENT_DURATION_MS)
    val segmentsBehind = Math.floorDiv(
        live.targetLatencyMs + segmentDurationMs - 1L,
        segmentDurationMs,
    )
    val targetSequence = (live.headSequence - segmentsBehind)
        .coerceIn(1L, Int.MAX_VALUE.toLong())
        .toInt()
    val targetTimeMs = (live.headTimeMs - live.targetLatencyMs).coerceAtLeast(0L)
    return SabrLiveWarmupTarget(targetSequence, targetTimeMs, segmentDurationMs)
}

internal fun SabrSessionHolder.advanceLiveWarmupTarget(
    target: SabrLiveWarmupTarget,
    segments: List<SabrMediaSegment>,
): SabrLiveWarmupTarget {
    val activeFormats = listOfNotNull(
        audioFormat.takeIf { isAudioActive() },
        videoFormat.takeIf { isVideoActive() },
    )
    val nextSequence = activeFormats.minOfOrNull { format ->
        val received = segments.asSequence()
            .filter { !it.header.isInitSegment && it.header.itag == format.itag }
            .map { it.header.sequenceNumber }
            .filter { it >= target.sequence }
            .toSet()
        var sequence = target.sequence
        while (sequence in received && sequence < Int.MAX_VALUE) sequence++
        sequence
    } ?: target.sequence
    val advancedBy = nextSequence - target.sequence
    return target.copy(
        sequence = nextSequence,
        timeMs = target.timeMs + advancedBy * target.segmentDurationMs,
    )
}

internal inline fun <T> withLiveWarmupRequestShape(
    holder: SabrSessionHolder,
    target: SabrLiveWarmupTarget?,
    block: () -> T,
): T {
    target ?: return block()
    val state = holder.session.streamState
    val ranges = buildList {
        if (holder.isAudioActive()) add(holder.audioFormat.liveWarmupRange(target.sequence, target.timeMs))
        if (holder.isVideoActive()) add(holder.videoFormat.liveWarmupRange(target.sequence, target.timeMs))
    }
    state.setPlayerTimeMs(target.timeMs)
    state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
    state.setBufferedRangesOverride(ranges)
    return try {
        block()
    } finally {
        state.setBufferedRangesOverride(null)
        state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
    }
}

private fun YoutubeSabrFormat.liveWarmupRange(targetSequence: Int, targetTimeMs: Long): SabrBufferedRange {
    val bufferedSequence = (targetSequence - 1).coerceAtLeast(0)
    return SabrBufferedRange(
        itag,
        lastModified,
        xtags,
        0L,
        targetTimeMs.coerceAtLeast(1L),
        if (bufferedSequence > 0) 1 else 0,
        bufferedSequence,
        TIMESCALE,
    )
}

private const val MIN_LIVE_SEGMENT_DURATION_MS = 100L
private const val MAX_LIVE_SEGMENT_DURATION_MS = 30_000L
private const val TIMESCALE = 1_000
