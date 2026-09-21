package dev.typetype.server.services

import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.WatchLaterItem

object HomeRecommendationDelayedCreditBuilder {
    fun buildVideoCredit(
        historyItems: List<HistoryItem>,
        favorites: List<FavoriteItem>,
        watchLater: List<WatchLaterItem>,
        playlists: List<PlaylistItem>,
        now: Long = System.currentTimeMillis(),
    ): Map<String, Double> {
        val scoreByUrl = mutableMapOf<String, Double>()
        historyItems.take(320).forEach { item ->
            if (item.url.isBlank()) return@forEach
            val progressRatio = if (item.duration > 0L) {
                (item.progress.toDouble() / item.duration.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.5
            }
            val weight = recencyWeight(item.watchedAt, now, halfLifeDays = 21.0)
            add(scoreByUrl, item.url, (0.10 + progressRatio * 0.35) * weight)
        }
        favorites.take(180).forEach { item ->
            if (item.videoUrl.isBlank()) return@forEach
            add(scoreByUrl, item.videoUrl, 0.65 * recencyWeight(item.favoritedAt, now, halfLifeDays = 45.0))
        }
        watchLater.take(180).forEach { item ->
            if (item.url.isBlank()) return@forEach
            add(scoreByUrl, item.url, 0.40 * recencyWeight(item.addedAt, now, halfLifeDays = 30.0))
        }
        playlists.take(80).forEach { playlist ->
            val playlistWeight = recencyWeight(playlist.createdAt, now, halfLifeDays = 60.0)
            playlist.videos.take(120).forEach { video ->
                if (video.url.isBlank()) return@forEach
                add(scoreByUrl, video.url, 0.20 * playlistWeight)
            }
        }
        return scoreByUrl
            .entries
            .sortedByDescending { it.value }
            .take(220)
            .associate { it.key to it.value.coerceIn(0.0, 2.5) }
    }

    fun buildChannelCredit(
        historyItems: List<HistoryItem>,
        now: Long = System.currentTimeMillis(),
    ): Map<String, Double> {
        val scoreByChannel = mutableMapOf<String, Double>()
        historyItems.take(320).forEach { item ->
            if (item.channelUrl.isBlank()) return@forEach
            val progressRatio = if (item.duration > 0L) {
                (item.progress.toDouble() / item.duration.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.5
            }
            val weight = recencyWeight(item.watchedAt, now, halfLifeDays = 30.0)
            add(scoreByChannel, item.channelUrl, (0.08 + progressRatio * 0.22) * weight)
        }
        return scoreByChannel
            .entries
            .sortedByDescending { it.value }
            .take(120)
            .associate { it.key to it.value.coerceIn(0.0, 2.0) }
    }

    private fun add(target: MutableMap<String, Double>, key: String, delta: Double) {
        target[key] = (target[key] ?: 0.0) + delta
    }

    private fun recencyWeight(timestamp: Long, now: Long, halfLifeDays: Double): Double {
        if (timestamp <= 0L) return 0.25
        val ageMs = (now - timestamp).coerceAtLeast(0L).toDouble()
        val halfLifeMs = halfLifeDays * 24.0 * 60.0 * 60.0 * 1000.0
        val scaled = ageMs / halfLifeMs
        return Math.pow(0.5, scaled).coerceIn(0.10, 1.0)
    }
}
