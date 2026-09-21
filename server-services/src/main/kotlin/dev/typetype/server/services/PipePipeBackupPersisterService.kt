package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.RestorePipePipeResultItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import java.util.UUID

class PipePipeBackupPersisterService {

    suspend fun persist(userId: String, snapshot: PipePipeBackupSnapshotItem): PipePipeBackupRestoreResult = DatabaseFactory.query {
        SubscriptionMutationLock.acquire(userId)
        clearUserData(userId)
        val avatarsByChannel = snapshot.subscriptions
            .mapNotNull { item -> item.url.takeIf { it.isNotBlank() }?.let { url -> url to item.avatarUrl } }
            .toMap()
        val history = insertHistory(userId, snapshot.history, avatarsByChannel)
        val subscriptions = insertSubscriptions(userId, snapshot.subscriptions)
        SubscriptionGroupMembershipCleaner.retain(userId, snapshot.subscriptions.map { it.url })
        val (playlists, playlistVideos) = insertPlaylists(userId, snapshot.playlists)
        val progress = insertProgress(userId, snapshot.progress)
        val searchHistory = insertSearchHistory(userId, snapshot.searchHistory)
        val minWatchedAt = snapshot.history.minOfOrNull { it.watchedAt } ?: 0L
        val maxWatchedAt = snapshot.history.maxOfOrNull { it.watchedAt } ?: 0L
        PipePipeBackupRestoreResult(
            counts = RestorePipePipeResultItem(history, subscriptions, playlists, playlistVideos, progress, searchHistory, timeMode = "raw", historyMinWatchedAt = minWatchedAt, historyMaxWatchedAt = maxWatchedAt),
            historyMinWatchedAt = minWatchedAt,
            historyMaxWatchedAt = maxWatchedAt,
        )
    }.also {
        SubscriptionFeedCacheInvalidation.invalidate(userId)
    }

    private fun clearUserData(userId: String) {
        HistoryTable.deleteWhere { HistoryTable.userId eq userId }
        SubscriptionsTable.deleteWhere { SubscriptionsTable.userId eq userId }
        PlaylistVideosTable.deleteWhere { PlaylistVideosTable.userId eq userId }
        PlaylistsTable.deleteWhere { PlaylistsTable.userId eq userId }
        ProgressTable.deleteWhere { ProgressTable.userId eq userId }
        SearchHistoryTable.deleteWhere { SearchHistoryTable.userId eq userId }
    }

    private fun insertHistory(userId: String, items: List<PipePipeBackupHistoryItem>, avatarsByChannel: Map<String, String>): Int =
        items.sumOf { item ->
            val title = item.title.ifBlank { YoutubeTypeTypeMapper.titleForUrl(item.url) }
            val thumbnail = item.thumbnail.ifBlank { YoutubeTypeTypeMapper.thumbnailForUrl(item.url) }
            HistoryTable.insertIgnore {
                it[id] = UUID.randomUUID().toString(); it[HistoryTable.userId] = userId; it[url] = item.url; it[HistoryTable.title] = title; it[HistoryTable.thumbnail] = thumbnail
                it[channelName] = item.uploader; it[channelUrl] = item.uploaderUrl; it[channelAvatar] = avatarsByChannel[item.uploaderUrl] ?: ""; it[duration] = item.duration; it[progress] = 0L; it[watchedAt] = item.watchedAt
            }.insertedCount
        }

    private fun insertSubscriptions(userId: String, items: List<PipePipeBackupSubscriptionItem>): Int =
        items.sumOf { item ->
            val canonicalUrl = ChannelUrlCanonicalizer.canonicalize(item.url)
            SubscriptionsTable.insertIgnore {
                it[SubscriptionsTable.userId] = userId; it[channelUrl] = canonicalUrl; it[name] = item.name; it[avatarUrl] = item.avatarUrl; it[subscribedAt] = System.currentTimeMillis()
            }.insertedCount
        }

    private fun insertPlaylists(userId: String, items: List<PipePipeBackupPlaylistItem>): Pair<Int, Int> {
        var playlists = 0
        var videos = 0
        items.forEach { item ->
            val playlistId = UUID.randomUUID().toString()
            playlists += PlaylistsTable.insertIgnore {
                it[id] = playlistId; it[PlaylistsTable.userId] = userId; it[name] = item.name; it[description] = ""; it[createdAt] = System.currentTimeMillis()
            }.insertedCount
            item.videos.forEachIndexed { index, video ->
                val title = video.title.ifBlank { YoutubeTypeTypeMapper.titleForUrl(video.url) }
                val thumbnail = video.thumbnail.ifBlank { YoutubeTypeTypeMapper.thumbnailForUrl(video.url) }
                videos += PlaylistVideosTable.insertIgnore {
                    it[id] = UUID.randomUUID().toString(); it[PlaylistVideosTable.playlistId] = playlistId; it[PlaylistVideosTable.userId] = userId
                    it[url] = video.url; it[PlaylistVideosTable.title] = title; it[PlaylistVideosTable.thumbnail] = thumbnail; it[duration] = video.duration; it[position] = index
                }.insertedCount
            }
        }
        return playlists to videos
    }

    private fun insertProgress(userId: String, items: List<PipePipeBackupProgressItem>): Int =
        items.sumOf { item ->
            ProgressTable.insertIgnore {
                it[ProgressTable.userId] = userId; it[videoUrl] = item.videoUrl; it[position] = item.position; it[updatedAt] = System.currentTimeMillis()
            }.insertedCount
        }

    private fun insertSearchHistory(userId: String, items: List<PipePipeBackupSearchHistoryItem>): Int =
        items.sumOf { item ->
            SearchHistoryTable.insertIgnore {
                it[id] = UUID.randomUUID().toString(); it[SearchHistoryTable.userId] = userId; it[term] = item.term; it[searchedAt] = item.searchedAt
            }.insertedCount
        }
}
