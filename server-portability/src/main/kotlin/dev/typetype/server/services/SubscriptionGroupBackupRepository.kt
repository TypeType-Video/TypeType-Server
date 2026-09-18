package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionGroupsTable
import dev.typetype.server.models.SubscriptionGroupBackupItem
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.Locale
import java.util.UUID

object SubscriptionGroupBackupRepository {
    suspend fun export(
        userId: String,
        subscriptionUrls: Set<String>,
    ): List<SubscriptionGroupBackupItem> = DatabaseFactory.query {
        val channelsByGroup = SubscriptionGroupMembershipsTable.selectAll()
            .where { SubscriptionGroupMembershipsTable.userId eq userId }
            .orderBy(SubscriptionGroupMembershipsTable.addedAt to SortOrder.ASC)
            .filter {
                ChannelUrlCanonicalizer.canonicalize(it[SubscriptionGroupMembershipsTable.channelUrl]) in subscriptionUrls
            }
            .groupBy(
                keySelector = { it[SubscriptionGroupMembershipsTable.groupId] },
                valueTransform = { it[SubscriptionGroupMembershipsTable.channelUrl] },
            )
        SubscriptionGroupsTable.selectAll()
            .where { SubscriptionGroupsTable.userId eq userId }
            .orderBy(SubscriptionGroupsTable.createdAt to SortOrder.ASC)
            .map { row ->
                SubscriptionGroupBackupItem(
                    name = row[SubscriptionGroupsTable.name],
                    channelUrls = channelsByGroup[row[SubscriptionGroupsTable.id]].orEmpty(),
                    createdAt = row[SubscriptionGroupsTable.createdAt],
                    updatedAt = row[SubscriptionGroupsTable.updatedAt],
                )
            }
    }

    fun restore(userId: String, items: List<SubscriptionGroupBackupItem>): Pair<Int, Int> {
        SubscriptionGroupMembershipsTable.deleteWhere { SubscriptionGroupMembershipsTable.userId eq userId }
        SubscriptionGroupsTable.deleteWhere { SubscriptionGroupsTable.userId eq userId }
        val groups = items.map { it to UUID.randomUUID().toString() }
        if (groups.isNotEmpty()) {
            SubscriptionGroupsTable.batchInsert(groups, shouldReturnGeneratedValues = false) { (item, id) ->
                this[SubscriptionGroupsTable.id] = id
                this[SubscriptionGroupsTable.userId] = userId
                this[SubscriptionGroupsTable.name] = item.name
                this[SubscriptionGroupsTable.normalizedName] = item.name.lowercase(Locale.ROOT)
                this[SubscriptionGroupsTable.createdAt] = item.createdAt
                this[SubscriptionGroupsTable.updatedAt] = item.updatedAt
            }
        }
        val memberships = groups.flatMap { (item, groupId) ->
            item.channelUrls.map { channelUrl -> groupId to ChannelUrlCanonicalizer.canonicalize(channelUrl) }
        }
        if (memberships.isNotEmpty()) {
            SubscriptionGroupMembershipsTable.batchInsert(memberships, shouldReturnGeneratedValues = false) { (groupId, channelUrl) ->
                this[SubscriptionGroupMembershipsTable.groupId] = groupId
                this[SubscriptionGroupMembershipsTable.userId] = userId
                this[SubscriptionGroupMembershipsTable.channelUrl] = channelUrl
                this[SubscriptionGroupMembershipsTable.addedAt] = System.currentTimeMillis()
            }
        }
        return groups.size to memberships.size
    }
}
