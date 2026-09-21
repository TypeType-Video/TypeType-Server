package dev.typetype.server.portability

import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionGroupsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

object TypeTypePortabilityCoreExport {
    fun write(userId: String, category: PortabilityCategory, sink: PortabilityRecordSink) {
        when (category) {
            PortabilityCategory.SUBSCRIPTIONS -> subscriptions(userId, sink)
            PortabilityCategory.SUBSCRIPTION_GROUPS -> groups(userId, sink)
            PortabilityCategory.HISTORY -> history(userId, sink)
            PortabilityCategory.PLAYLISTS -> playlists(userId, sink)
            else -> error("Unsupported core portability category")
        }
    }

    private fun subscriptions(userId: String, sink: PortabilityRecordSink) {
        SubscriptionsTable.selectAll().where { SubscriptionsTable.userId eq userId }
            .orderBy(SubscriptionsTable.subscribedAt, SortOrder.ASC)
            .forEach { row ->
                sink.write(
                    PortabilitySubscription(
                        row[SubscriptionsTable.channelUrl],
                        row[SubscriptionsTable.name],
                        row[SubscriptionsTable.avatarUrl],
                        row[SubscriptionsTable.subscribedAt],
                    ),
                )
            }
    }

    private fun groups(userId: String, sink: PortabilityRecordSink) {
        val groups = SubscriptionGroupsTable.selectAll()
            .where { SubscriptionGroupsTable.userId eq userId }
            .orderBy(SubscriptionGroupsTable.createdAt, SortOrder.ASC)
        groups.forEach { row ->
            val groupId = row[SubscriptionGroupsTable.id]
            val name = row[SubscriptionGroupsTable.name]
            sink.write(PortabilitySubscriptionGroup(name))
            SubscriptionGroupMembershipsTable.selectAll().where {
                SubscriptionGroupMembershipsTable.groupId eq groupId
            }.orderBy(SubscriptionGroupMembershipsTable.addedAt, SortOrder.ASC).forEach { membership ->
                sink.write(
                    PortabilitySubscriptionGroupMembership(
                        name,
                        membership[SubscriptionGroupMembershipsTable.channelUrl],
                    ),
                )
            }
        }
    }

    private fun history(userId: String, sink: PortabilityRecordSink) {
        HistoryTable.selectAll().where { HistoryTable.userId eq userId }
            .orderBy(HistoryTable.watchedAt, SortOrder.ASC)
            .forEach { row ->
                sink.write(
                    PortabilityHistory(
                        video = PortabilityVideo(
                            url = row[HistoryTable.url],
                            title = row[HistoryTable.title],
                            thumbnailUrl = row[HistoryTable.thumbnail],
                            durationSeconds = row[HistoryTable.duration],
                            channelName = row[HistoryTable.channelName],
                            channelUrl = row[HistoryTable.channelUrl],
                            channelAvatarUrl = row[HistoryTable.channelAvatar],
                        ),
                        watchedAt = row[HistoryTable.watchedAt],
                        positionSeconds = row[HistoryTable.progress],
                    ),
                )
            }
    }

    private fun playlists(userId: String, sink: PortabilityRecordSink) {
        PlaylistsTable.selectAll().where { PlaylistsTable.userId eq userId }
            .orderBy(PlaylistsTable.createdAt, SortOrder.ASC)
            .forEach { row ->
                val id = row[PlaylistsTable.id]
                sink.write(
                    PortabilityPlaylist(
                        id,
                        row[PlaylistsTable.name],
                        row[PlaylistsTable.description],
                        row[PlaylistsTable.createdAt],
                    ),
                )
                playlistVideos(userId, id, sink)
            }
    }

    private fun playlistVideos(userId: String, playlistId: String, sink: PortabilityRecordSink) {
        PlaylistVideosTable.selectAll().where {
            (PlaylistVideosTable.userId eq userId) and (PlaylistVideosTable.playlistId eq playlistId)
        }.orderBy(PlaylistVideosTable.position, SortOrder.ASC).forEach { row ->
            sink.write(
                PortabilityPlaylistVideo(
                    playlistId,
                    row[PlaylistVideosTable.position],
                    PortabilityVideo(
                        row[PlaylistVideosTable.url],
                        row[PlaylistVideosTable.title],
                        row[PlaylistVideosTable.thumbnail],
                        row[PlaylistVideosTable.duration],
                        row[PlaylistVideosTable.channelName],
                        row[PlaylistVideosTable.channelUrl],
                        row[PlaylistVideosTable.channelAvatar],
                        row[PlaylistVideosTable.viewCount],
                        row[PlaylistVideosTable.publishedAt],
                    ),
                    row[PlaylistVideosTable.addedAt],
                ),
            )
        }
    }
}
