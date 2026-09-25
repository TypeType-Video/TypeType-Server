package dev.typetype.server.services

import dev.typetype.server.PlaybackTraceLog
import dev.typetype.server.sabr.YoutubeSabrFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

data class SabrPlaybackInitializationPreloadResult(
    val video: ByteArray?,
    val audio: ByteArray?,
) {
    fun isComplete(audioOnly: Boolean): Boolean =
        audio != null && (audioOnly || video != null)

    fun missingTracks(audioOnly: Boolean, videoItag: Int, audioItag: Int): String =
        buildList {
            if (!audioOnly && video == null) add("video:$videoItag")
            if (audio == null) add("audio:$audioItag")
        }.joinToString()
}

object SabrPlaybackInitializationPreloader {
    suspend fun preload(
        sessionStore: SabrSessionStore,
        holder: SabrSessionHolder,
        audioOnly: Boolean,
        timeoutMs: Long,
    ): SabrPlaybackInitializationPreloadResult = withTimeoutOrNull(timeoutMs) {
        coroutineScope {
            val video = holder.videoFormat
                .takeUnless { audioOnly }
                ?.let { format -> async { fetchTrack(sessionStore, holder, format, "video") } }
            val audio = async { fetchTrack(sessionStore, holder, holder.audioFormat, "audio") }
            SabrPlaybackInitializationPreloadResult(video?.await(), audio.await())
        }
    } ?: SabrPlaybackInitializationPreloadResult(null, null)

    private suspend fun fetchTrack(
        sessionStore: SabrSessionStore,
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        track: String,
    ): ByteArray? {
        val startedAt = System.nanoTime()
        val fields = "videoId=${holder.key.videoId} track=$track itag=${format.itag}"
        PlaybackTraceLog.record("sabr_init_preload_start", fields)
        val bytes = try {
            sessionStore.fetchInitializationData(holder, format)
        } catch (error: CancellationException) {
            PlaybackTraceLog.record(
                "sabr_init_preload_complete",
                "$fields durationMs=${elapsedMs(startedAt)} result=cancelled",
            )
            throw error
        } catch (error: Exception) {
            PlaybackTraceLog.record(
                "sabr_init_preload_complete",
                "$fields durationMs=${elapsedMs(startedAt)} result=failed errorType=${error.javaClass.simpleName}",
            )
            throw error
        }
        PlaybackTraceLog.record(
            "sabr_init_preload_complete",
            "$fields durationMs=${elapsedMs(startedAt)} result=${if (bytes == null) "miss" else "hit"} bytes=${bytes?.size ?: 0}",
        )
        return bytes
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
