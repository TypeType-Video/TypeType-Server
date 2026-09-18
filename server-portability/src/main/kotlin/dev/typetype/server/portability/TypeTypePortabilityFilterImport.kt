package dev.typetype.server.portability

import dev.typetype.server.db.tables.AllowedChannelsTable
import dev.typetype.server.db.tables.AllowedPlaylistsTable
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedKeywordsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.services.normalizeBlockedKeyword
import dev.typetype.server.services.normalizeChannelKey
import dev.typetype.server.services.normalizePlaylistKey
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

internal object TypeTypePortabilityFilterImport {
    private const val USER_SCOPE = "user"

    fun write(
        userId: String,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
        onRecord: () -> Unit,
    ): Long {
        if (policy == PortabilityDuplicatePolicy.REPLACE) clear(userId)
        var count = 0L
        source.forEach(PortabilityCategory.CONTENT_FILTERS) { record ->
            if (record is PortabilityContentFilter) count += insert(userId, record)
            onRecord()
        }
        return count
    }

    private fun insert(userId: String, record: PortabilityContentFilter): Int = when (record.kind) {
        "blockedChannel" -> BlockedChannelsTable.insertIgnore {
            it[BlockedChannelsTable.userId] = userId
            it[scope] = USER_SCOPE
            it[channelUrl] = record.value
            it[channelName] = record.label
            it[channelThumbnailUrl] = record.imageUrl
            it[blockedAt] = record.createdAt
        }.insertedCount
        "blockedVideo" -> BlockedVideosTable.insertIgnore {
            it[BlockedVideosTable.userId] = userId
            it[scope] = USER_SCOPE
            it[videoUrl] = record.value
            it[blockedAt] = record.createdAt
        }.insertedCount
        "blockedKeyword" -> BlockedKeywordsTable.insertIgnore {
            it[BlockedKeywordsTable.userId] = userId
            it[scope] = USER_SCOPE
            it[keyword] = normalizeBlockedKeyword(record.value)
            it[blockedAt] = record.createdAt
        }.insertedCount
        "allowedChannel" -> AllowedChannelsTable.insertIgnore {
            it[AllowedChannelsTable.userId] = userId
            it[scope] = USER_SCOPE
            it[channelUrl] = normalizeChannelKey(record.value)
            it[channelName] = record.label
            it[channelThumbnailUrl] = record.imageUrl
            it[allowedAt] = record.createdAt
        }.insertedCount
        "allowedPlaylist" -> allowedPlaylist(userId, record)
        else -> throw IllegalArgumentException("Unsupported content filter kind")
    }

    private fun allowedPlaylist(userId: String, record: PortabilityContentFilter): Int {
        val url = normalizePlaylistKey(record.value)
        val exists = AllowedPlaylistsTable.selectAll().where {
            (AllowedPlaylistsTable.userId eq userId) and
                (AllowedPlaylistsTable.scope eq USER_SCOPE) and
                (AllowedPlaylistsTable.playlistUrl eq url)
        }.empty().not()
        if (exists) return 0
        return AllowedPlaylistsTable.insertIgnore {
            it[AllowedPlaylistsTable.userId] = userId
            it[scope] = USER_SCOPE
            it[playlistUrl] = url
            it[title] = record.label
            it[thumbnailUrl] = record.imageUrl
            it[uploaderName] = record.metadata["uploaderName"]?.jsonPrimitive?.content.orEmpty()
            it[allowedAt] = record.createdAt
        }.insertedCount
    }

    private fun clear(userId: String) {
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
