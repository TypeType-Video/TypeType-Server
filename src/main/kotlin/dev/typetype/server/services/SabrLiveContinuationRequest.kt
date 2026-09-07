package dev.typetype.server.services

import dev.typetype.server.sabr.SabrBufferedRange
import dev.typetype.server.sabr.YoutubeSabrFormat

internal inline fun <T> withLiveContinuationRequestShape(
    holder: SabrSessionHolder,
    block: () -> T,
): T {
    val ranges = buildList {
        holder.continuationRange(holder.audioFormat)?.takeIf { holder.isAudioActive() }?.let(::add)
        holder.continuationRange(holder.videoFormat)?.takeIf { holder.isVideoActive() }?.let(::add)
    }
    if (ranges.isEmpty()) return block()
    val state = holder.session.streamState
    state.setPlayerTimeMs(holder.playerTimeMs())
    state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
    state.setBufferedRangesOverride(ranges)
    return try {
        block()
    } finally {
        state.setBufferedRangesOverride(null)
        state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
    }
}

private fun SabrSessionHolder.continuationRange(format: YoutubeSabrFormat): SabrBufferedRange? {
    observedMediaSegment(format) ?: return null
    val sequence = lastServedSequence(format)
        ?: (playbackStartSequence(format, requestedSeekTimeMs() ?: playerTimeMs()) - 1).coerceAtLeast(0)
    val bufferedEndMs = playbackSegmentEndMs(format, sequence).coerceAtLeast(1L)
    return SabrBufferedRange(
        format.itag,
        format.lastModified,
        format.xtags,
        0L,
        bufferedEndMs,
        if (sequence > 0) 1 else 0,
        sequence,
        TIMESCALE,
    )
}

private const val TIMESCALE = 1_000
