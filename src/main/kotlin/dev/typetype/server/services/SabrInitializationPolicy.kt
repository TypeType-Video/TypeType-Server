package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat

internal object SabrInitializationPolicy {
    fun warmFormats(
        audioOnly: Boolean,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ): List<YoutubeSabrFormat> = if (audioOnly) listOf(audio) else listOf(video, audio)

    fun requiresVideoFirst(audioOnly: Boolean, audio: Boolean, playerTimeMs: Long): Boolean =
        !audioOnly && audio && playerTimeMs > 0L
}
