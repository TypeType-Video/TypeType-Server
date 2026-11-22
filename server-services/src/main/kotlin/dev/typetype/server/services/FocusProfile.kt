package dev.typetype.server.services

import java.util.zip.CRC32

enum class FocusChannelAccess {
    ALLOW_STUDY,
    ENTERTAINMENT,
    PENDING_STUDY,
    OUTSIDE_STUDY,
}

sealed interface FocusBlockReason {
    data object Entertainment : FocusBlockReason
    data class StudyPending(val unlockAt: Long) : FocusBlockReason
    data object OutsideStudy : FocusBlockReason
}

data class FocusProfile(
    val seed: String,
    val entertainmentChannels: Set<String>,
    val studyChannels: Set<String> = emptySet(),
    val pendingStudyUnlocksAt: Map<String, Long> = emptyMap(),
    val entertainmentShare: Double,
    val archiveDays: Int,
    val archiveCutoffMs: Long,
    val strictBlocking: Boolean = false,
    val temporaryAllowedVideos: Set<String> = emptySet(),
) {
    fun isEntertainment(channelUrl: String): Boolean =
        channelUrl.isNotBlank() && normalizeChannelKey(channelUrl) in entertainmentChannels

    fun isStudy(channelUrl: String): Boolean =
        channelUrl.isNotBlank() && !isEntertainment(channelUrl) && normalizeChannelKey(channelUrl) in studyChannels

    fun pendingUnlockAt(channelUrl: String): Long? {
        if (channelUrl.isBlank() || isEntertainment(channelUrl)) return null
        return pendingStudyUnlocksAt[normalizeChannelKey(channelUrl)]
    }

    fun accessFor(channelUrl: String): FocusChannelAccess = when {
        isEntertainment(channelUrl) -> FocusChannelAccess.ENTERTAINMENT
        isStudy(channelUrl) -> FocusChannelAccess.ALLOW_STUDY
        pendingUnlockAt(channelUrl) != null -> FocusChannelAccess.PENDING_STUDY
        else -> FocusChannelAccess.OUTSIDE_STUDY
    }

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

    fun keepsFeedVideo(url: String, uploaderUrl: String, publishedAt: Long? = null): Boolean =
        when (accessFor(uploaderUrl)) {
            FocusChannelAccess.ALLOW_STUDY -> true
            FocusChannelAccess.ENTERTAINMENT -> withinArchive(publishedAt) && keepsVideo(url, uploaderUrl)
            FocusChannelAccess.PENDING_STUDY,
            FocusChannelAccess.OUTSIDE_STUDY,
            -> !strictBlocking
        }

    fun isTemporaryAllowed(url: String): Boolean =
        url.isNotBlank() && normalizeBlockedVideoKey(url) in temporaryAllowedVideos

    fun blockReasonForPlayback(url: String, uploaderUrl: String, publishedAt: Long? = null): FocusBlockReason? {
        if (!strictBlocking) return null
        if (uploaderUrl.isBlank()) {
            return if (isTemporaryAllowed(url)) null else FocusBlockReason.OutsideStudy
        }
        return when (accessFor(uploaderUrl)) {
            FocusChannelAccess.ALLOW_STUDY -> null
            FocusChannelAccess.ENTERTAINMENT ->
                if (withinArchive(publishedAt) && keepsVideo(url, uploaderUrl)) null else FocusBlockReason.Entertainment
            FocusChannelAccess.PENDING_STUDY ->
                if (isTemporaryAllowed(url)) null else FocusBlockReason.StudyPending(requireNotNull(pendingUnlockAt(uploaderUrl)))
            FocusChannelAccess.OUTSIDE_STUDY ->
                if (isTemporaryAllowed(url)) null else FocusBlockReason.OutsideStudy
        }
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
