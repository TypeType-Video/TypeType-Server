package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.WatchLaterTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        repairPlaylists(userId)
    }

    fun scheduleWatchLater(scope: CoroutineScope, userId: String): Unit = schedule(scope, "watch-later:$userId") {
        repairWatchLater(userId)
    }

    fun scheduleFavorites(scope: CoroutineScope, userId: String): Unit = schedule(scope, "favorites:$userId") {
        repairFavorites(userId)
    }

    suspend fun repairPlaylists(userId: String): Int = repair(userId, ::playlistCandidateUrls)

    suspend fun repairWatchLater(userId: String): Int = repair(userId, ::watchLaterCandidateUrls)

    suspend fun repairFavorites(userId: String): Int = repair(userId, ::favoriteCandidateUrls)

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

    private suspend fun repair(userId: String, candidates: suspend (String) -> List<String>): Int {
        val urls = candidates(userId).take(MAX_REPAIR_PER_REQUEST)
        if (urls.isEmpty()) return 0
        val metadata = resolver.resolve(urls)
        if (metadata.isEmpty()) return 0
        return DatabaseFactory.query {
            metadata.values.sumOf { item ->
                updatePlaylistVideos(userId, item) + updateWatchLater(userId, item) + updateFavorites(userId, item)
            }
        }
    }

    private suspend fun playlistCandidateUrls(userId: String): List<String> = DatabaseFactory.query {
        PlaylistVideosTable.selectAll()
            .where { (PlaylistVideosTable.userId eq userId) and playlistNeedsRepair() }
            .limit(MAX_REPAIR_PER_REQUEST)
            .map { it[PlaylistVideosTable.url] }
            .distinct()
    }

    private suspend fun watchLaterCandidateUrls(userId: String): List<String> = DatabaseFactory.query {
        WatchLaterTable.selectAll()
            .where { (WatchLaterTable.userId eq userId) and watchLaterNeedsRepair() }
            .limit(MAX_REPAIR_PER_REQUEST)
            .map { it[WatchLaterTable.url] }
            .distinct()
    }

    private suspend fun favoriteCandidateUrls(userId: String): List<String> = DatabaseFactory.query {
        FavoritesTable.selectAll()
            .where { (FavoritesTable.userId eq userId) and favoriteNeedsRepair() }
            .limit(MAX_REPAIR_PER_REQUEST)
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
        const val REPAIR_COOLDOWN_MS = 5 * 60 * 1000L
    }
}
