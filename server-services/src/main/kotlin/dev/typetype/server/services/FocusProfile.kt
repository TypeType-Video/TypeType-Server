package dev.typetype.server.services

import java.util.zip.CRC32

data class FocusProfile(
    val seed: String,
    val entertainmentChannels: Set<String>,
    val entertainmentShare: Double,
    val archiveDays: Int,
    val archiveCutoffMs: Long,
) {
    fun isEntertainment(channelUrl: String): Boolean = normalizeChannelKey(channelUrl) in entertainmentChannels

    // A hash of the profile and the video keeps the same videos hidden on every page and every reload,
    // so the share drops without the feed reshuffling.
    fun keepsVideo(url: String, uploaderUrl: String): Boolean {
        if (!isEntertainment(uploaderUrl)) return true
        if (entertainmentShare >= 1.0) return true
        if (entertainmentShare <= 0.0) return false
        val crc = CRC32()
        crc.update("$seed|${normalizeBlockedVideoKey(url)}".toByteArray())
        return crc.value.toDouble() / HASH_RANGE < entertainmentShare
    }

    fun withinArchive(publishedAt: Long?): Boolean = publishedAt == null || publishedAt >= archiveCutoffMs

    companion object {
        const val DAY_MS = 86_400_000L
        private const val HASH_RANGE = 4_294_967_296.0

        fun share(startedAt: Long, transitionDays: Int, now: Long): Double {
            val elapsed = (now - startedAt).coerceAtLeast(0L).toDouble()
            return (1.0 - elapsed / (transitionDays * DAY_MS)).coerceIn(0.0, 1.0)
        }
    }
}
