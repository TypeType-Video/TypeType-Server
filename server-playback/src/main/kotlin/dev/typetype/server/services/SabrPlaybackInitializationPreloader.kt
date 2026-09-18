package dev.typetype.server.services

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
                ?.let { format -> async { sessionStore.fetchInitializationData(holder, format) } }
            val audio = async { sessionStore.fetchInitializationData(holder, holder.audioFormat) }
            SabrPlaybackInitializationPreloadResult(video?.await(), audio.await())
        }
    } ?: SabrPlaybackInitializationPreloadResult(null, null)
}
