package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.StreamResponse

object AudioOnlyStreamSelector {
    fun progressiveCandidates(
        response: StreamResponse,
        preferOriginal: Boolean,
        preferredLocale: String?,
    ): List<AudioStreamItem> =
        response.audioStreams
            .filter { it.url.isNotBlank() && !isManifestUrl(it.url) && browserSafeRank(it) != null }
            .sortedWith(defaultComparator(response, preferOriginal, preferredLocale)
                .thenBy { browserSafeRank(it) ?: Int.MAX_VALUE })

    fun hlsCandidates(response: StreamResponse, preferOriginal: Boolean, preferredLocale: String?): List<AudioStreamItem> =
        response.audioStreams
            .filter { it.url.isNotBlank() && isManifestUrl(it.url) }
            .sortedWith(defaultComparator(response, preferOriginal, preferredLocale))

    fun sabrCandidates(response: StreamResponse, preferOriginal: Boolean, preferredLocale: String?): List<AudioStreamItem> =
        response.audioStreams
            .filter { it.deliveryMethod == "sabr" && !it.sabrSessionUrl.isNullOrBlank() && isSabrDashSafeAudio(it) }
            .sortedWith(defaultComparator(response, preferOriginal, preferredLocale))

    private fun defaultComparator(
        response: StreamResponse,
        preferOriginal: Boolean,
        preferredLocale: String?,
    ): Comparator<AudioStreamItem> =
        compareBy<AudioStreamItem> { preferenceRank(it, preferOriginal, preferredLocale, response) }
            .thenByDescending { it.bitrate ?: 0 }

    private fun preferenceRank(
        stream: AudioStreamItem,
        preferOriginal: Boolean,
        preferredLocale: String?,
        response: StreamResponse,
    ): Int {
        if (preferOriginal && stream.isOriginal) return 0
        if (!preferredLocale.isNullOrBlank() && matchesLocale(stream, preferredLocale)) return 1
        if (stream.audioTrackId != null && stream.audioTrackId == response.preferredDefaultAudioTrackId) return 2
        if (!preferOriginal && stream.isOriginal) return 3
        return 4
    }

    private fun browserSafeRank(stream: AudioStreamItem): Int? {
        val mime = stream.mimeType.lowercase().substringBefore(";").trim()
        val codec = stream.codec?.lowercase().orEmpty()
        if (mime == "audio/mp4" && (codec.isBlank() || codec.startsWith("mp4a"))) return 0
        if (mime == "audio/webm" && (codec.isBlank() || codec.startsWith("opus"))) return 1
        return null
    }

    private fun isSabrDashSafeAudio(stream: AudioStreamItem): Boolean {
        val mime = stream.mimeType.lowercase().substringBefore(";").trim()
        val codec = stream.codec?.lowercase().orEmpty()
        return mime == "audio/mp4" && (codec.isBlank() || codec.startsWith("mp4a"))
    }

    private fun matchesLocale(stream: AudioStreamItem, preferredLocale: String): Boolean {
        val wanted = preferredLocale.normalizedLocale()
        val locale = stream.audioLocale?.normalizedLocale()
        if (locale != null && locale == wanted) return true
        val track = stream.audioTrackId?.substringBefore('.')?.normalizedLocale()
        return track != null && track == wanted
    }

    private fun String.normalizedLocale(): String = lowercase().replace('_', '-').substringBefore('-')
}
