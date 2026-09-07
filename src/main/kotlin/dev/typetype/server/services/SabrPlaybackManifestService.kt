package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat

internal class SabrPlaybackManifestService {
    fun build(holder: SabrSessionHolder, mediaBasePath: String): SabrPlaybackManifestResult {
        val state = holder.session.streamState
        val knownAudio = maxOf(state.getEndSegment(holder.audioFormat), state.getMaxSegment(holder.audioFormat).toLong())
        val knownVideo = maxOf(state.getEndSegment(holder.videoFormat), state.getMaxSegment(holder.videoFormat).toLong())
        if (knownAudio <= 0L || knownVideo <= 0L) return SabrPlaybackManifestResult.Retry(holder.playbackStatus())
        val startMs = holder.playerTimeMs().coerceAtLeast(0L)
        val startAudio = segmentAt(holder, holder.audioFormat, startMs, knownAudio)
        val startVideo = segmentAt(holder, holder.videoFormat, startMs, knownVideo)
        val startAudioMs = state.getSegmentStartMs(holder.audioFormat, startAudio).coerceAtLeast(0L)
        val startVideoMs = state.getSegmentStartMs(holder.videoFormat, startVideo).coerceAtLeast(0L)
        val readyAtMs = maxOf(startAudioMs, startVideoMs)
        val bufferedEdgeMs = state.getMinBufferedEndMs()
        if (bufferedEdgeMs < readyAtMs) return SabrPlaybackManifestResult.Retry(holder.playbackStatus())
        val edgeMs = bufferedEdgeMs.coerceAtLeast(startMs)
        val windowEndMs = edgeMs + PLAYBACK_MANIFEST_WINDOW_MS
        val windowEndAudio = segmentAt(holder, holder.audioFormat, windowEndMs, knownAudio)
        val windowEndVideo = segmentAt(holder, holder.videoFormat, windowEndMs, knownVideo)
        val generation = holder.activeGeneration()
        return SabrPlaybackManifestResult.Ready(
            SabrManifestBuilder.build(
                videoId = holder.key.videoId,
                audio = holder.audioFormat,
                video = holder.videoFormat,
                endSegmentAudio = maxOf(startAudio, windowEndAudio).toLong(),
                endSegmentVideo = maxOf(startVideo, windowEndVideo).toLong(),
                streamState = state,
                sessionToken = holder.sessionToken,
                startSegmentAudio = startAudio,
                startSegmentVideo = startVideo,
                mediaBasePath = mediaBasePath,
                extraSegmentQuery = "&generation=$generation",
            ),
        )
    }

    private fun segmentAt(holder: SabrSessionHolder, format: YoutubeSabrFormat, ms: Long, maxSegment: Long): Int =
        holder.playbackStartSequence(format, ms)
            .coerceAtLeast(1)
            .coerceAtMost(maxSegment.toInt().coerceAtLeast(1))

    private fun SabrSessionHolder.playbackStatus(): String = playbackState().name.lowercase().takeIf { it != IDLE }
        ?: PREPARING

    private companion object {
        const val PLAYBACK_MANIFEST_WINDOW_MS = 30_000L
        const val IDLE = "idle"
        const val PREPARING = "preparing"
    }
}

internal sealed class SabrPlaybackManifestResult {
    data class Ready(val manifest: String) : SabrPlaybackManifestResult()
    data class Retry(val status: String) : SabrPlaybackManifestResult()
}
