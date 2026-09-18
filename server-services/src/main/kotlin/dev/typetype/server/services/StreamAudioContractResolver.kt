package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.StreamResponse

object StreamAudioContractResolver {
    fun apply(
        response: StreamResponse,
        fallbackLanguage: String = defaultFallbackLanguage(),
    ): StreamResponse {
        val normalized = response.audioStreams.map { stream ->
            stream.copy(isOriginal = isOriginalTrack(stream))
        }
        val originalTrackId = normalized.firstNotNullOfOrNull { stream ->
            stream.audioTrackId?.takeIf { stream.isOriginal }
        }
        val preferredTrackId = resolvePreferredTrackId(normalized, fallbackLanguage, originalTrackId)
        return response.copy(
            audioStreams = normalized,
            originalAudioTrackId = originalTrackId,
            preferredDefaultAudioTrackId = preferredTrackId,
        )
    }

    private fun resolvePreferredTrackId(
        streams: List<AudioStreamItem>,
        fallbackLanguage: String,
        originalTrackId: String?,
    ): String? {
        if (originalTrackId != null) return originalTrackId
        val fallbackTrackId = streams.firstNotNullOfOrNull { stream ->
            stream.audioTrackId?.takeIf { matchesFallback(stream, fallbackLanguage) }
        }
        if (fallbackTrackId != null) return fallbackTrackId
        return streams.firstNotNullOfOrNull { it.audioTrackId }
    }

    private fun matchesFallback(stream: AudioStreamItem, fallbackLanguage: String): Boolean {
        val wanted = normalizedLanguage(fallbackLanguage)
        val locale = stream.audioLocale?.let(::normalizedLanguage)
        if (locale != null && locale == wanted) return true
        val trackLanguage = stream.audioTrackId?.substringBefore('.')?.let(::normalizedLanguage)
        return trackLanguage != null && trackLanguage == wanted
    }

    private fun isOriginalTrack(stream: AudioStreamItem): Boolean {
        val lowered = stream.audioTrackName?.lowercase() ?: return false
        if ("original" in lowered) return true
        if ("default" in lowered) return true
        if ("yokuqala" in lowered) return true
        return false
    }

    private fun normalizedLanguage(language: String): String = language.lowercase().substringBefore('-')

    private fun defaultFallbackLanguage(): String =
        System.getenv(AUDIO_FALLBACK_ENV)?.trim().orEmpty().ifBlank { DEFAULT_FALLBACK_LANGUAGE }

    private const val AUDIO_FALLBACK_ENV = "DEFAULT_AUDIO_FALLBACK_LANGUAGE"
    private const val DEFAULT_FALLBACK_LANGUAGE = "en"
}
