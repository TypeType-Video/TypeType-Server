package dev.typetype.server.services

import dev.typetype.server.db.tables.AllowedChannelsTable
import dev.typetype.server.db.tables.AllowedPlaylistsTable
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedKeywordsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.models.TypeTypeContentFiltersBackup
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere

private const val USER_SCOPE = "user"

internal object TypeTypeBackupFilterRestore {
    fun restore(userId: String, backup: TypeTypeContentFiltersBackup): Map<String, Int> {
        deleteUserFilters(userId)
        BlockedChannelsTable.batchInsert(backup.blockedChannels, false) { item ->
            this[BlockedChannelsTable.userId] = userId
            this[BlockedChannelsTable.scope] = USER_SCOPE
            this[BlockedChannelsTable.channelUrl] = item.url
            this[BlockedChannelsTable.channelName] = item.name
            this[BlockedChannelsTable.channelThumbnailUrl] = item.thumbnailUrl
            this[BlockedChannelsTable.blockedAt] = item.blockedAt
        }
        BlockedVideosTable.batchInsert(backup.blockedVideos, false) { item ->
            this[BlockedVideosTable.userId] = userId
            this[BlockedVideosTable.scope] = USER_SCOPE
            this[BlockedVideosTable.videoUrl] = item.url
            this[BlockedVideosTable.blockedAt] = item.blockedAt
        }
        BlockedKeywordsTable.batchInsert(backup.blockedKeywords, false) { item ->
            this[BlockedKeywordsTable.userId] = userId
            this[BlockedKeywordsTable.scope] = USER_SCOPE
            this[BlockedKeywordsTable.keyword] = normalizeBlockedKeyword(item.keyword)
            this[BlockedKeywordsTable.blockedAt] = item.blockedAt
        }
        AllowedChannelsTable.batchInsert(backup.allowedChannels, false) { item ->
            this[AllowedChannelsTable.userId] = userId
            this[AllowedChannelsTable.scope] = USER_SCOPE
            this[AllowedChannelsTable.channelUrl] = normalizeChannelKey(item.url)
            this[AllowedChannelsTable.channelName] = item.name
            this[AllowedChannelsTable.channelThumbnailUrl] = item.thumbnailUrl
            this[AllowedChannelsTable.allowedAt] = item.allowedAt
        }
        AllowedPlaylistsTable.batchInsert(backup.allowedPlaylists, false) { item ->
            this[AllowedPlaylistsTable.userId] = userId
            this[AllowedPlaylistsTable.scope] = USER_SCOPE
            this[AllowedPlaylistsTable.playlistUrl] = normalizePlaylistKey(item.url)
            this[AllowedPlaylistsTable.title] = item.title
            this[AllowedPlaylistsTable.thumbnailUrl] = item.thumbnailUrl
            this[AllowedPlaylistsTable.uploaderName] = item.uploaderName
            this[AllowedPlaylistsTable.allowedAt] = item.allowedAt
        }
        return linkedMapOf(
            "blockedChannels" to backup.blockedChannels.size,
            "blockedVideos" to backup.blockedVideos.size,
            "blockedKeywords" to backup.blockedKeywords.size,
            "allowedChannels" to backup.allowedChannels.size,
            "allowedPlaylists" to backup.allowedPlaylists.size,
        )
    }

    private fun deleteUserFilters(userId: String) {
        BlockedChannelsTable.deleteWhere {
            (BlockedChannelsTable.userId eq userId) and (BlockedChannelsTable.scope eq USER_SCOPE)
        }
        BlockedVideosTable.deleteWhere {
            (BlockedVideosTable.userId eq userId) and (BlockedVideosTable.scope eq USER_SCOPE)
        }
        BlockedKeywordsTable.deleteWhere {
            (BlockedKeywordsTable.userId eq userId) and (BlockedKeywordsTable.scope eq USER_SCOPE)
        }
        AllowedChannelsTable.deleteWhere {
            (AllowedChannelsTable.userId eq userId) and (AllowedChannelsTable.scope eq USER_SCOPE)
        }
        AllowedPlaylistsTable.deleteWhere {
            (AllowedPlaylistsTable.userId eq userId) and (AllowedPlaylistsTable.scope eq USER_SCOPE)
        }
    }
}
