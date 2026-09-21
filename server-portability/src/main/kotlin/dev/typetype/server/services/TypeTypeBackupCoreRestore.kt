package dev.typetype.server.services

import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.SubscriptionItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.util.UUID

internal object TypeTypeBackupCoreRestore {
    fun subscriptions(userId: String, items: List<SubscriptionItem>): Int {
        SubscriptionsTable.deleteWhere { SubscriptionsTable.userId eq userId }
        SubscriptionsTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            this[SubscriptionsTable.userId] = userId
            this[SubscriptionsTable.channelUrl] = ChannelUrlCanonicalizer.canonicalize(item.channelUrl)
            this[SubscriptionsTable.name] = item.name
            this[SubscriptionsTable.avatarUrl] = item.avatarUrl
            this[SubscriptionsTable.subscribedAt] = item.subscribedAt
        }
        SubscriptionGroupMembershipCleaner.retain(userId, items.map(SubscriptionItem::channelUrl))
        return items.size
    }

    fun history(userId: String, items: List<HistoryItem>): Int {
        HistoryTable.deleteWhere { HistoryTable.userId eq userId }
        HistoryTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            this[HistoryTable.id] = UUID.randomUUID().toString()
            this[HistoryTable.userId] = userId
            this[HistoryTable.url] = item.url
            this[HistoryTable.title] = item.title
            this[HistoryTable.thumbnail] = item.thumbnail
            this[HistoryTable.channelName] = item.channelName
            this[HistoryTable.channelUrl] = item.channelUrl
            this[HistoryTable.channelAvatar] = item.channelAvatar
            this[HistoryTable.duration] = item.duration
            this[HistoryTable.progress] = item.progress
            this[HistoryTable.watchedAt] = item.watchedAt
        }
        return items.size
    }

    fun playlists(userId: String, items: List<PlaylistItem>): Pair<Int, Int> {
        PlaylistVideosTable.deleteWhere { PlaylistVideosTable.userId eq userId }
        PlaylistsTable.deleteWhere { PlaylistsTable.userId eq userId }
        var videoCount = 0
        items.forEach { playlist ->
            val playlistId = UUID.randomUUID().toString()
            PlaylistsTable.batchInsert(listOf(playlist), shouldReturnGeneratedValues = false) {
                this[PlaylistsTable.id] = playlistId
                this[PlaylistsTable.userId] = userId
                this[PlaylistsTable.name] = it.name
                this[PlaylistsTable.description] = it.description
                this[PlaylistsTable.createdAt] = it.createdAt
            }
            PlaylistVideosTable.batchInsert(
                playlist.videos,
                shouldReturnGeneratedValues = false,
            ) { video ->
                this[PlaylistVideosTable.id] = UUID.randomUUID().toString()
                this[PlaylistVideosTable.playlistId] = playlistId
                this[PlaylistVideosTable.userId] = userId
                this[PlaylistVideosTable.url] = video.url
                this[PlaylistVideosTable.title] = video.title
                this[PlaylistVideosTable.thumbnail] = video.thumbnail
                this[PlaylistVideosTable.duration] = video.duration
                this[PlaylistVideosTable.position] = video.position
                this[PlaylistVideosTable.channelName] = video.channelName
                this[PlaylistVideosTable.channelUrl] = video.channelUrl
                this[PlaylistVideosTable.channelAvatar] = video.channelAvatar
                this[PlaylistVideosTable.viewCount] = video.viewCount
                this[PlaylistVideosTable.addedAt] = video.addedAt
                this[PlaylistVideosTable.publishedAt] = video.publishedAt
            }
            videoCount += playlist.videos.size
        }
        return items.size to videoCount
    }
}
