package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.WatchLaterTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class UserVideoMetadataRepairService(private val resolver: VideoMetadataResolver) {
    private val logger = LoggerFactory.getLogger(UserVideoMetadataRepairService::class.java)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastScheduledAt = ConcurrentHashMap<String, Long>()

    fun schedulePlaylists(scope: CoroutineScope, userId: String): Unit = schedule(scope, "playlists:$userId") {
        drain { excluded -> repairPlaylists(userId, excluded) }
    }

    fun scheduleWatchLater(scope: CoroutineScope, userId: String): Unit = schedule(scope, "watch-later:$userId") {
        drain { excluded -> repairWatchLater(userId, excluded) }
    }

    fun scheduleFavorites(scope: CoroutineScope, userId: String): Unit = schedule(scope, "favorites:$userId") {
        drain { excluded -> repairFavorites(userId, excluded) }
    }

    private suspend fun repairPlaylists(userId: String, excluded: Set<String>): RepairOutcome =
        repair(userId, excluded, ::playlistCandidateUrls)

    private suspend fun repairWatchLater(userId: String, excluded: Set<String>): RepairOutcome =
        repair(userId, excluded, ::watchLaterCandidateUrls)

    private suspend fun repairFavorites(userId: String, excluded: Set<String>): RepairOutcome =
        repair(userId, excluded, ::favoriteCandidateUrls)

    private suspend fun drain(repair: suspend (Set<String>) -> RepairOutcome) {
        val excluded = mutableSetOf<String>()
        repeat(MAX_BATCHES_PER_RUN) {
            val outcome = repair(excluded)
            if (outcome.attempted == 0) return
            excluded += outcome.attemptedUrls
            delay(BATCH_DELAY_MS)
        }
    }

    private fun schedule(scope: CoroutineScope, key: String, repair: suspend () -> Unit) {
        val now = System.currentTimeMillis()
        val lastScheduled = lastScheduledAt[key]
        if (lastScheduled != null && now - lastScheduled < REPAIR_COOLDOWN_MS) return
        if (!inFlight.add(key)) return
        lastScheduledAt[key] = now
        scope.launch(Dispatchers.IO) {
            try {
                repair()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.warn("Background video metadata repair failed", error)
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private suspend fun repair(
        userId: String,
        excluded: Set<String>,
        candidates: suspend (String) -> List<String>,
    ): RepairOutcome {
        val urls = candidates(userId).filterNot { it in excluded }.take(MAX_REPAIR_PER_REQUEST)
        if (urls.isEmpty()) return RepairOutcome(attemptedUrls = emptySet(), updated = 0)
        val first = resolver.resolveDetailed(urls)
        var updated = applyMetadata(userId, first)
        if (first.retryableUrls.isNotEmpty()) {
            delay(RETRY_DELAY_MS)
            val retried = resolver.resolveDetailed(first.retryableUrls)
            updated += applyMetadata(userId, retried)
        }
        return RepairOutcome(attemptedUrls = urls.toSet(), updated = updated)
    }

    private suspend fun applyMetadata(userId: String, resolution: VideoMetadataResolution): Int =
        DatabaseFactory.query {
            resolution.metadata.values.sumOf { item ->
                updatePlaylistVideos(userId, item) + updateWatchLater(userId, item) + updateFavorites(userId, item)
            }
        }

    private suspend fun playlistCandidateUrls(userId: String): List<String> = DatabaseFactory.query {
        PlaylistVideosTable.selectAll()
            .where { (PlaylistVideosTable.userId eq userId) and playlistNeedsRepair() }
            .map { it[PlaylistVideosTable.url] }
            .distinct()
    }

    private suspend fun watchLaterCandidateUrls(userId: String): List<String> = DatabaseFactory.query {
        WatchLaterTable.selectAll()
            .where { (WatchLaterTable.userId eq userId) and watchLaterNeedsRepair() }
            .map { it[WatchLaterTable.url] }
            .distinct()
    }

    private suspend fun favoriteCandidateUrls(userId: String): List<String> = DatabaseFactory.query {
        FavoritesTable.selectAll()
            .where { (FavoritesTable.userId eq userId) and favoriteNeedsRepair() }
            .map { it[FavoritesTable.videoUrl] }
            .distinct()
    }

    private fun playlistNeedsRepair() =
        (PlaylistVideosTable.title like FALLBACK_TITLE_PATTERN) or
            (PlaylistVideosTable.thumbnail like YOUTUBE_THUMB_PATTERN) or
            (PlaylistVideosTable.duration lessEq 0L) or
            (PlaylistVideosTable.channelName eq "") or
            (PlaylistVideosTable.channelUrl eq "")

    private fun watchLaterNeedsRepair() =
        (WatchLaterTable.title like FALLBACK_TITLE_PATTERN) or
            (WatchLaterTable.thumbnail like YOUTUBE_THUMB_PATTERN) or
            (WatchLaterTable.duration lessEq 0L) or
            (WatchLaterTable.channelName eq "") or
            (WatchLaterTable.channelUrl eq "")

    private fun favoriteNeedsRepair() =
        (FavoritesTable.title like FALLBACK_TITLE_PATTERN) or
            (FavoritesTable.thumbnail like YOUTUBE_THUMB_PATTERN) or
            (FavoritesTable.duration lessEq 0L) or
            (FavoritesTable.channelName eq "") or
            (FavoritesTable.channelUrl eq "")

    private fun updatePlaylistVideos(userId: String, item: VideoMetadataItem): Int = PlaylistVideosTable.update({
        (PlaylistVideosTable.userId eq userId) and (PlaylistVideosTable.url eq item.url)
    }) {
        it[title] = item.title; it[thumbnail] = item.thumbnail; it[duration] = item.duration
        it[channelName] = item.channelName; it[channelUrl] = item.channelUrl; it[channelAvatar] = item.channelAvatar
        it[viewCount] = item.viewCount; it[publishedAt] = item.publishedAt
    }

    private fun updateWatchLater(userId: String, item: VideoMetadataItem): Int = WatchLaterTable.update({
        (WatchLaterTable.userId eq userId) and (WatchLaterTable.url eq item.url)
    }) {
        it[title] = item.title; it[thumbnail] = item.thumbnail; it[duration] = item.duration
        it[channelName] = item.channelName; it[channelUrl] = item.channelUrl; it[channelAvatar] = item.channelAvatar
        it[viewCount] = item.viewCount; it[publishedAt] = item.publishedAt
    }

    private fun updateFavorites(userId: String, item: VideoMetadataItem): Int = FavoritesTable.update({
        (FavoritesTable.userId eq userId) and (FavoritesTable.videoUrl eq item.url)
    }) {
        it[title] = item.title; it[thumbnail] = item.thumbnail; it[duration] = item.duration
        it[channelName] = item.channelName; it[channelUrl] = item.channelUrl; it[channelAvatar] = item.channelAvatar
        it[viewCount] = item.viewCount; it[publishedAt] = item.publishedAt
    }

    private companion object {
        const val FALLBACK_TITLE_PATTERN = "YouTube video %"
        const val YOUTUBE_THUMB_PATTERN = "https://i.ytimg.com/vi/%"
        const val MAX_REPAIR_PER_REQUEST = 25
        const val MAX_BATCHES_PER_RUN = 8
        const val BATCH_DELAY_MS = 750L
        const val RETRY_DELAY_MS = 2_000L
        const val REPAIR_COOLDOWN_MS = 5 * 60 * 1000L
    }
}

private data class RepairOutcome(
    val attemptedUrls: Set<String>,
    val updated: Int,
) {
    val attempted: Int get() = attemptedUrls.size
}
