package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment
import org.slf4j.LoggerFactory

internal class SabrPlaybackWarmer {
    suspend fun preflight(store: SabrSessionStore, holder: SabrSessionHolder, playerTimeMs: Long): Boolean {
        holder.setActiveTracks(videoActive = true, audioActive = true)
        store.ensureWarmed(holder)
        val videoInit = store.fetchInitializationData(holder, holder.videoFormat) ?: return false
        val audioInit = store.fetchInitializationData(holder, holder.audioFormat) ?: return false
        if (videoInit.isEmpty() || audioInit.isEmpty()) return false
        var positionMs = playerTimeMs.coerceAtLeast(0L)
        val targetMs = positionMs + INITIAL_WINDOW_MS
        var hasVideo = false
        var hasAudio = false
        while (positionMs <= targetMs) {
            val segments = store.fetchMediaAt(holder, positionMs).orEmpty()
            if (segments.isEmpty()) return false
            hasVideo = hasVideo || segments.hasItag(holder.videoFormat.itag)
            hasAudio = hasAudio || segments.hasItag(holder.audioFormat.itag)
            val next = segments.minOf { it.header.startMs + it.header.durationMs }
            positionMs = maxOf(next, positionMs + 1L)
        }
        logger.info("sabr_preflight event=window_ready videoId={} startMs={} endMs={} audio={} video={}", holder.key.videoId, playerTimeMs, positionMs, hasAudio, hasVideo)
        return hasVideo && hasAudio
    }

    private fun List<SabrMediaSegment>.hasItag(itag: Int): Boolean =
        any { !it.header.isInitSegment && it.header.itag == itag }

    private companion object {
        val logger = LoggerFactory.getLogger(SabrPlaybackWarmer::class.java)
        const val INITIAL_WINDOW_MS = 2_500L
    }
}
