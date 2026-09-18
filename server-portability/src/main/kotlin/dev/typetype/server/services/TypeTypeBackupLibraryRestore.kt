package dev.typetype.server.services

import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SavedPlaylistsTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.ProgressItem
import dev.typetype.server.models.SavedPlaylistItem
import dev.typetype.server.models.SearchHistoryItem
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.models.WatchLaterItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

internal object TypeTypeBackupLibraryRestore {
    fun watchLater(userId: String, items: List<WatchLaterItem>): Int {
        WatchLaterTable.deleteWhere { WatchLaterTable.userId eq userId }
        WatchLaterTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            this[WatchLaterTable.userId] = userId
            this[WatchLaterTable.url] = item.url
            this[WatchLaterTable.title] = item.title
            this[WatchLaterTable.thumbnail] = item.thumbnail
            this[WatchLaterTable.duration] = item.duration
            this[WatchLaterTable.addedAt] = item.addedAt
            this[WatchLaterTable.channelName] = item.channelName
            this[WatchLaterTable.channelUrl] = item.channelUrl
            this[WatchLaterTable.channelAvatar] = item.channelAvatar
            this[WatchLaterTable.viewCount] = item.viewCount
            this[WatchLaterTable.publishedAt] = item.publishedAt
        }
        return items.size
    }

    fun favorites(userId: String, items: List<FavoriteItem>): Int {
        FavoritesTable.deleteWhere { FavoritesTable.userId eq userId }
        FavoritesTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            this[FavoritesTable.userId] = userId
            this[FavoritesTable.videoUrl] = item.videoUrl
            this[FavoritesTable.favoritedAt] = item.favoritedAt
            this[FavoritesTable.title] = item.title
            this[FavoritesTable.thumbnail] = item.thumbnail
            this[FavoritesTable.duration] = item.duration
            this[FavoritesTable.channelName] = item.channelName
            this[FavoritesTable.channelUrl] = item.channelUrl
            this[FavoritesTable.channelAvatar] = item.channelAvatar
            this[FavoritesTable.viewCount] = item.viewCount
            this[FavoritesTable.publishedAt] = item.publishedAt
        }
        return items.size
    }

    fun progress(userId: String, items: List<ProgressItem>): Int {
        ProgressTable.deleteWhere { ProgressTable.userId eq userId }
        ProgressTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            this[ProgressTable.userId] = userId
            this[ProgressTable.videoUrl] = item.videoUrl
            this[ProgressTable.position] = item.position.coerceAtLeast(0)
            this[ProgressTable.updatedAt] = item.updatedAt
        }
        return items.size
    }

    fun searchHistory(userId: String, items: List<SearchHistoryItem>): Int {
        SearchHistoryTable.deleteWhere { SearchHistoryTable.userId eq userId }
        SearchHistoryTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            this[SearchHistoryTable.id] = UUID.randomUUID().toString()
            this[SearchHistoryTable.userId] = userId
            this[SearchHistoryTable.term] = item.term
            this[SearchHistoryTable.searchedAt] = item.searchedAt
        }
        return items.size
    }

    fun savedPlaylists(userId: String, items: List<SavedPlaylistItem>): Int {
        SavedPlaylistsTable.deleteWhere { SavedPlaylistsTable.userId eq userId }
        SavedPlaylistsTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            this[SavedPlaylistsTable.id] = UUID.randomUUID().toString()
            this[SavedPlaylistsTable.userId] = userId
            this[SavedPlaylistsTable.publicPlaylistId] = item.publicPlaylistId
            this[SavedPlaylistsTable.url] = item.url
            this[SavedPlaylistsTable.title] = item.title
            this[SavedPlaylistsTable.thumbnailUrl] = item.thumbnailUrl
            this[SavedPlaylistsTable.uploaderName] = item.uploaderName
            this[SavedPlaylistsTable.streamCount] = item.streamCount
            this[SavedPlaylistsTable.playlistType] = item.playlistType
            this[SavedPlaylistsTable.savedAt] = item.savedAt
        }
        return items.size
    }

    fun settings(userId: String, item: SettingsItem): Int {
        val normalized = item.normalized()
        val updated = SettingsTable.update({ SettingsTable.userId eq userId }) {
            it.writeSettings(normalized)
        }
        if (updated == 0) {
            SettingsTable.insert {
                it[SettingsTable.userId] = userId
                it.writeSettings(normalized)
            }
        }
        return 1
    }
}
