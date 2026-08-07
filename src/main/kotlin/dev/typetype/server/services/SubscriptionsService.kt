package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.SubscriptionItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class SubscriptionsService {

    suspend fun getAll(
        userId: String,
        selection: SubscriptionSelection = SubscriptionSelection.All,
    ): List<SubscriptionItem> = DatabaseFactory.query {
        val selectedUrls = selectedChannelUrls(userId, selection)
        val items = SubscriptionsTable.selectAll()
            .where { SubscriptionsTable.userId eq userId }
            .orderBy(SubscriptionsTable.subscribedAt to SortOrder.DESC)
            .map { it.toItem() }
            .filter { selection == SubscriptionSelection.All || it.channelUrl in selectedUrls }
        SubscriptionAvatarRepairer.repair(userId = userId, items = items)
    }

    suspend fun getChannelUrls(userId: String, selection: SubscriptionSelection): Set<String> =
        DatabaseFactory.query { selectedChannelUrls(userId, selection) }

    suspend fun add(userId: String, item: SubscriptionItem): SubscriptionItem {
        val canonicalUrl = ChannelUrlCanonicalizer.canonicalize(item.channelUrl)
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            SubscriptionsTable.insert {
                it[SubscriptionsTable.userId] = userId
                it[channelUrl] = canonicalUrl
                it[name] = item.name
                it[avatarUrl] = item.avatarUrl
                it[subscribedAt] = now
            }
        }
        return item.copy(channelUrl = canonicalUrl, subscribedAt = now)
    }

    suspend fun delete(userId: String, channelUrl: String): Boolean = DatabaseFactory.query {
        val canonicalUrl = ChannelUrlCanonicalizer.canonicalize(channelUrl)
        SubscriptionGroupMembershipsTable.deleteWhere {
            (SubscriptionGroupMembershipsTable.userId eq userId) and
                (SubscriptionGroupMembershipsTable.channelUrl eq canonicalUrl)
        }
        SubscriptionsTable.deleteWhere { SubscriptionsTable.channelUrl eq canonicalUrl and (SubscriptionsTable.userId eq userId) } > 0
    }

    private fun selectedChannelUrls(userId: String, selection: SubscriptionSelection): Set<String> {
        val all = SubscriptionsTable.selectAll()
            .where { SubscriptionsTable.userId eq userId }
            .mapTo(linkedSetOf()) { ChannelUrlCanonicalizer.canonicalize(it[SubscriptionsTable.channelUrl]) }
        if (selection == SubscriptionSelection.All) return all
        val memberships = SubscriptionGroupMembershipsTable.selectAll().where {
            when (selection) {
                SubscriptionSelection.All -> SubscriptionGroupMembershipsTable.userId eq userId
                SubscriptionSelection.Ungrouped -> SubscriptionGroupMembershipsTable.userId eq userId
                is SubscriptionSelection.Group ->
                    (SubscriptionGroupMembershipsTable.userId eq userId) and
                        (SubscriptionGroupMembershipsTable.groupId eq selection.id)
            }
        }.mapTo(mutableSetOf()) { it[SubscriptionGroupMembershipsTable.channelUrl] }
        return when (selection) {
            SubscriptionSelection.All -> all
            SubscriptionSelection.Ungrouped -> all - memberships
            is SubscriptionSelection.Group -> all intersect memberships
        }
    }

    private fun ResultRow.toItem() = SubscriptionItem(
        channelUrl = ChannelUrlCanonicalizer.canonicalize(this[SubscriptionsTable.channelUrl]),
        name = this[SubscriptionsTable.name],
        avatarUrl = this[SubscriptionsTable.avatarUrl],
        subscribedAt = this[SubscriptionsTable.subscribedAt],
    )
}
