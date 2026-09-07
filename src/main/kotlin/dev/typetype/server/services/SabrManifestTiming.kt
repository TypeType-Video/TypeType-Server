package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrStreamState

internal object SabrManifestTiming {
    fun videoDurationSec(
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        streamState: YoutubeSabrStreamState,
    ): Long {
        val endSegment = streamState.getEndSegment(video).toInt()
        val indexMs = if (endSegment > 0) streamState.getSegmentEndMs(video, endSegment) else 0L
        val ms = maxOf(indexMs, audio.approxDurationMs, video.approxDurationMs, 0L)
        return (ms / 1000L).coerceAtLeast(1L)
    }

    fun audioDurationSec(audio: YoutubeSabrFormat, streamState: YoutubeSabrStreamState): Long {
        val endSegment = streamState.getEndSegment(audio).toInt()
        val indexMs = if (endSegment > 0) streamState.getSegmentEndMs(audio, endSegment) else 0L
        val ms = maxOf(indexMs, audio.approxDurationMs, 0L)
        return (ms / 1000L).coerceAtLeast(1L)
    }

    fun averageSegmentMs(format: YoutubeSabrFormat, segmentCount: Long): Long {
        val totalMs = format.approxDurationMs.coerceAtLeast(1L)
        return (totalMs / segmentCount.coerceAtLeast(1L)).coerceAtLeast(1L)
    }

    fun timelineDurationMs(
        format: YoutubeSabrFormat,
        seq: Int,
        streamState: YoutubeSabrStreamState,
        fallbackMs: Long,
    ): Long {
        val start = streamState.getSegmentStartMs(format, seq).coerceAtLeast(0L)
        val end = streamState.getSegmentEndMs(format, seq)
        return (end - start).takeIf { it > 0L } ?: fallbackMs
    }

    fun segmentDurationMs(
        format: YoutubeSabrFormat,
        seq: Int,
        endSegment: Int,
        streamState: YoutubeSabrStreamState,
    ): Long {
        val start = streamState.getSegmentStartMs(format, seq).coerceAtLeast(0L)
        val end = streamState.getSegmentEndMs(format, seq)
        if (end > start) return end - start
        val totalMs = format.approxDurationMs.coerceAtLeast(1L)
        return (totalMs / endSegment.coerceAtLeast(1)).coerceAtLeast(1L)
    }
}
