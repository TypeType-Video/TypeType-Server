package dev.typetype.server.portability

import dev.typetype.server.db.tables.AllowedChannelsTable
import dev.typetype.server.db.tables.AllowedPlaylistsTable
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedKeywordsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

object TypeTypePortabilityFilterExport {
    private const val USER_SCOPE = "user"

    fun write(userId: String, sink: PortabilityRecordSink) {
        blockedChannels(userId, sink)
        blockedVideos(userId, sink)
        blockedKeywords(userId, sink)
        allowedChannels(userId, sink)
        allowedPlaylists(userId, sink)
    }

    private fun blockedChannels(userId: String, sink: PortabilityRecordSink) {
        BlockedChannelsTable.selectAll().where {
            (BlockedChannelsTable.userId eq userId) and (BlockedChannelsTable.scope eq USER_SCOPE)
        }.forEach { row ->
            sink.write(
                PortabilityContentFilter(
                    "blockedChannel",
                    row[BlockedChannelsTable.channelUrl],
                    row[BlockedChannelsTable.channelName].orEmpty(),
                    row[BlockedChannelsTable.channelThumbnailUrl].orEmpty(),
                    row[BlockedChannelsTable.blockedAt],
                ),
            )
        }
    }

    private fun blockedVideos(userId: String, sink: PortabilityRecordSink) {
        BlockedVideosTable.selectAll().where {
            (BlockedVideosTable.userId eq userId) and (BlockedVideosTable.scope eq USER_SCOPE)
        }.forEach { row ->
            sink.write(
                PortabilityContentFilter(
                    "blockedVideo",
                    row[BlockedVideosTable.videoUrl],
                    createdAt = row[BlockedVideosTable.blockedAt],
                ),
            )
        }
    }

    private fun blockedKeywords(userId: String, sink: PortabilityRecordSink) {
        BlockedKeywordsTable.selectAll().where {
            (BlockedKeywordsTable.userId eq userId) and (BlockedKeywordsTable.scope eq USER_SCOPE)
        }.forEach { row ->
            sink.write(
                PortabilityContentFilter(
                    "blockedKeyword",
                    row[BlockedKeywordsTable.keyword],
                    createdAt = row[BlockedKeywordsTable.blockedAt],
                ),
            )
        }
    }

    private fun allowedChannels(userId: String, sink: PortabilityRecordSink) {
        AllowedChannelsTable.selectAll().where {
            (AllowedChannelsTable.userId eq userId) and (AllowedChannelsTable.scope eq USER_SCOPE)
        }.forEach { row ->
            sink.write(
                PortabilityContentFilter(
                    "allowedChannel",
                    row[AllowedChannelsTable.channelUrl],
                    row[AllowedChannelsTable.channelName].orEmpty(),
                    row[AllowedChannelsTable.channelThumbnailUrl].orEmpty(),
                    row[AllowedChannelsTable.allowedAt],
                ),
            )
        }
    }

    private fun allowedPlaylists(userId: String, sink: PortabilityRecordSink) {
        AllowedPlaylistsTable.selectAll().where {
            (AllowedPlaylistsTable.userId eq userId) and (AllowedPlaylistsTable.scope eq USER_SCOPE)
        }.forEach { row ->
            sink.write(
                PortabilityContentFilter(
                    "allowedPlaylist",
                    row[AllowedPlaylistsTable.playlistUrl],
                    row[AllowedPlaylistsTable.title].orEmpty(),
                    row[AllowedPlaylistsTable.thumbnailUrl].orEmpty(),
                    row[AllowedPlaylistsTable.allowedAt],
                    buildJsonObject {
                        put("uploaderName", row[AllowedPlaylistsTable.uploaderName].orEmpty())
                    },
                ),
            )
        }
    }
}
