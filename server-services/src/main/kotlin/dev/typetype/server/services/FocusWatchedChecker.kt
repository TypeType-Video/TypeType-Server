package dev.typetype.server.services

import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SettingsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

object FocusWatchedChecker {
    private const val SHORT_VIDEO_SECONDS = 60L
    private const val LONG_VIDEO_TAIL_SECONDS = 60L
    private const val SHORT_VIDEO_WATCHED_RATIO = 0.9
    private const val MIN_CUSTOM_THRESHOLD = 50
    private const val MAX_CUSTOM_THRESHOLD = 100
    private const val MS_PER_SECOND = 1_000L

    fun isWatched(progressSeconds: Long, durationSeconds: Long, thresholdPercent: Int = 0): Boolean {
        if (progressSeconds <= 0L || durationSeconds <= 0L) return false
        val bounded = minOf(progressSeconds, durationSeconds)
        if (thresholdPercent in MIN_CUSTOM_THRESHOLD..MAX_CUSTOM_THRESHOLD) {
            return bounded.toDouble() >= (durationSeconds.toDouble() * thresholdPercent) / 100.0
        }
        return if (durationSeconds > SHORT_VIDEO_SECONDS) {
            bounded >= durationSeconds - LONG_VIDEO_TAIL_SECONDS
        } else {
            bounded.toDouble() >= durationSeconds.toDouble() * SHORT_VIDEO_WATCHED_RATIO
        }
    }

    internal fun isVideoWatchedInTx(
        userId: String,
        videoUrl: String,
        streamDurationSeconds: Long,
        isLive: Boolean,
    ): Boolean {
        if (isLive || videoUrl.isBlank()) return false
        val targetKey = normalizeBlockedVideoKey(videoUrl)
        if (targetKey.isBlank()) return false

        val threshold = SettingsTable.select(SettingsTable.watchedThresholdPercent)
            .where { SettingsTable.userId eq userId }
            .singleOrNull()
            ?.get(SettingsTable.watchedThresholdPercent)
            ?: 0

        var maxProgressSec = 0L
        var fallbackDurationSec = 0L

        HistoryTable.select(HistoryTable.url, HistoryTable.progress, HistoryTable.duration)
            .where { HistoryTable.userId eq userId }
            .forEach { row ->
                if (normalizeBlockedVideoKey(row[HistoryTable.url]) == targetKey) {
                    maxProgressSec = maxOf(maxProgressSec, row[HistoryTable.progress])
                    fallbackDurationSec = maxOf(fallbackDurationSec, row[HistoryTable.duration])
                }
            }

        ProgressTable.select(ProgressTable.videoUrl, ProgressTable.position)
            .where { ProgressTable.userId eq userId }
            .forEach { row ->
                if (normalizeBlockedVideoKey(row[ProgressTable.videoUrl]) == targetKey) {
                    val posSec = row[ProgressTable.position].coerceAtLeast(0L) / MS_PER_SECOND
                    maxProgressSec = maxOf(maxProgressSec, posSec)
                }
            }

        val duration = streamDurationSeconds.takeIf { it > 0L } ?: fallbackDurationSec
        return isWatched(maxProgressSec, duration, threshold)
    }
}
