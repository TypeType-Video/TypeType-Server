package dev.typetype.server.services

import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionGroupsTable
import dev.typetype.server.models.SubscriptionGroupItem
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

internal object SubscriptionGroupQueries {
    fun all(userId: String): List<SubscriptionGroupItem> {
        val count = SubscriptionGroupMembershipsTable.channelUrl.count()
        val counts = SubscriptionGroupMembershipsTable.select(SubscriptionGroupMembershipsTable.groupId, count)
            .where { SubscriptionGroupMembershipsTable.userId eq userId }
            .groupBy(SubscriptionGroupMembershipsTable.groupId)
            .associate { it[SubscriptionGroupMembershipsTable.groupId] to it[count].toInt() }
        return SubscriptionGroupsTable.selectAll()
            .where { SubscriptionGroupsTable.userId eq userId }
            .orderBy(SubscriptionGroupsTable.createdAt to SortOrder.DESC)
            .map { row -> SubscriptionGroupItem(
                id = row[SubscriptionGroupsTable.id],
                name = row[SubscriptionGroupsTable.name],
                channelCount = counts[row[SubscriptionGroupsTable.id]] ?: 0,
                createdAt = row[SubscriptionGroupsTable.createdAt],
                updatedAt = row[SubscriptionGroupsTable.updatedAt],
            ) }
    }

    fun exists(userId: String, groupId: String): Boolean = SubscriptionGroupsTable.selectAll().where {
        (SubscriptionGroupsTable.id eq groupId) and (SubscriptionGroupsTable.userId eq userId)
    }.any()
}
