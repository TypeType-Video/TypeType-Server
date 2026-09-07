package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.livePlaybackSnapshot
import dev.typetype.server.services.playbackSegmentDurationMs
import dev.typetype.server.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.durationMs(): Long {
    livePlaybackSnapshot()?.let { live ->
        if (live.active) return live.seekableEndMs
    }
    val audioEndMs = indexedEndMs(audioFormat)
    val videoEndMs = indexedEndMs(videoFormat)
    if (audioEndMs > 0L && videoEndMs > 0L) return maxOf(audioEndMs, videoEndMs)
    return maxOf(audioFormat.approxDurationMs, videoFormat.approxDurationMs, 0L)
}

internal fun SabrSessionHolder.indexedEndMs(format: YoutubeSabrFormat): Long {
    val endSequence = session.streamState.getEndSegment(format).toInt()
    return if (endSequence > 0) session.streamState.getSegmentEndMs(format, endSequence) else 0L
}

internal fun SabrSessionHolder.readyEndMs(format: YoutubeSabrFormat, requestedEndMs: Long): Long {
    livePlaybackSnapshot()?.let { live ->
        if (live.active) return requestedEndMs
    }
    val durationMs = indexedEndMs(format).takeIf { it > 0L } ?: format.approxDurationMs.coerceAtLeast(0L)
    return minOf(durationMs, requestedEndMs)
}

internal fun readyAheadMs(request: SabrPlaybackWindowRequest, activeLive: Boolean): Long {
    val minimum = if (activeLive && request.bufferedRanges.isEmpty()) {
        LIVE_STARTUP_READY_AHEAD_MS
    } else {
        MIN_READY_AHEAD_MS
    }
    return minOf(request.bufferGoalMs.coerceAtLeast(1L), minimum)
}

internal fun YoutubeSabrFormat.trackName(): String = if (isAudio) "audio" else "video"

internal fun SabrPlaybackWindowRequest.bufferedEndFor(
    format: YoutubeSabrFormat,
    requestedStartMs: Long,
): Long {
    val startMs = requestedStartMs.coerceAtLeast(0L)
    return bufferedRanges.asSequence()
        .filter { it.itag == format.itag && it.startMs <= startMs && it.endMs > startMs }
        .maxOfOrNull { it.endMs }
        ?.coerceAtLeast(startMs)
        ?: startMs
}

internal fun resolvedPlaybackStartMs(
    requestedStartMs: Long,
    activeLive: Boolean,
    vararg tracks: SabrPlaybackWindowTrack?,
): Long {
    if (!activeLive) return requestedStartMs
    return tracks.asSequence()
        .filterNotNull()
        .mapNotNull { it.segments.firstOrNull()?.startMs }
        .fold(requestedStartMs.coerceAtLeast(0L), ::maxOf)
}

internal fun SabrSessionHolder.previousPlaybackSequence(
    format: YoutubeSabrFormat,
    sequence: Int,
    segment: CachedSabrSegment,
    targetMs: Long,
): Int {
    val durationMs = segment.durationMs.takeIf { it > 0L }
        ?: playbackSegmentDurationMs(format, sequence)
    val leadMs = segment.startMs - targetMs
    val count = ((leadMs + durationMs - 1L) / durationMs).coerceAtLeast(1L)
    return (sequence - count.coerceAtMost((sequence - 1).toLong()).toInt()).coerceAtLeast(1)
}

private const val MIN_READY_AHEAD_MS = 1_000L
private const val LIVE_STARTUP_READY_AHEAD_MS = 8_000L
