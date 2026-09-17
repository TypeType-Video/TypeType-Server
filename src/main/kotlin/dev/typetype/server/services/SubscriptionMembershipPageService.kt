package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.SubscriptionGroupMembershipItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.SubscriptionMembershipPage
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.Locale

class SubscriptionMembershipPageService {
    suspend fun getPage(userId: String, filter: SubscriptionMembershipFilter): SubscriptionMembershipPage =
        DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            val owned = SubscriptionsTable.userId eq userId
            val membershipUrls = SubscriptionGroupMembershipsTable
                .select(SubscriptionGroupMembershipsTable.channelUrl)
                .where { SubscriptionGroupMembershipsTable.userId eq userId }
            val ungrouped = SubscriptionsTable.channelUrl notInSubQuery membershipUrls
            val matching = owned and filterCondition(userId, filter)
            val total = SubscriptionsTable.selectAll().where { matching }.count()
            val items = SubscriptionsTable.selectAll().where { matching }
                .orderBy(SubscriptionsTable.name.lowerCase() to SortOrder.ASC, SubscriptionsTable.channelUrl to SortOrder.ASC)
                .limit(filter.limit).offset(filter.page.toLong() * filter.limit)
                .map { it.toItem() }
            SubscriptionMembershipPage(
                items = withMemberships(userId, items),
                total = total,
                totalSubscriptions = SubscriptionsTable.selectAll().where { owned }.count(),
                ungroupedCount = SubscriptionsTable.selectAll().where { owned and ungrouped }.count(),
                page = filter.page,
                limit = filter.limit,
            )
        }

    suspend fun lookup(userId: String, channelUrls: List<String>): List<SubscriptionGroupMembershipItem> =
        DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            val urls = channelUrls.mapTo(linkedSetOf(), ChannelUrlCanonicalizer::canonicalize)
            val items = SubscriptionsTable.selectAll().where {
                (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.channelUrl inList urls)
            }.map { it.toItem() }
            withMemberships(userId, items)
        }

    private fun filterCondition(userId: String, filter: SubscriptionMembershipFilter): Op<Boolean> {
        val membershipUrls = SubscriptionGroupMembershipsTable
            .select(SubscriptionGroupMembershipsTable.channelUrl)
            .where {
                val owned = SubscriptionGroupMembershipsTable.userId eq userId
                filter.groupId?.let { owned and (SubscriptionGroupMembershipsTable.groupId eq it) } ?: owned
            }
        val membership = when {
            filter.ungrouped || filter.excluded -> SubscriptionsTable.channelUrl notInSubQuery membershipUrls
            filter.groupId != null -> SubscriptionsTable.channelUrl inSubQuery membershipUrls
            else -> Op.TRUE
        }
        val search = filter.search.trim().lowercase(Locale.ROOT)
        if (search.isEmpty()) return membership
        val pattern = "%${search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        return membership and ((SubscriptionsTable.name.lowerCase() like pattern) or
            (SubscriptionsTable.channelUrl.lowerCase() like pattern))
    }

    private fun withMemberships(userId: String, items: List<SubscriptionItem>): List<SubscriptionGroupMembershipItem> {
        if (items.isEmpty()) return emptyList()
        val urls = items.map { it.channelUrl }
        val groups = SubscriptionGroupMembershipsTable.selectAll().where {
            (SubscriptionGroupMembershipsTable.userId eq userId) and
                (SubscriptionGroupMembershipsTable.channelUrl inList urls)
        }.groupBy({ it[SubscriptionGroupMembershipsTable.channelUrl] }, { it[SubscriptionGroupMembershipsTable.groupId] })
        return SubscriptionAvatarRepairer.repair(userId = userId, items = items).map { item ->
            SubscriptionGroupMembershipItem(
                channelUrl = item.channelUrl,
                name = item.name,
                avatarUrl = item.avatarUrl,
                subscribedAt = item.subscribedAt,
                groupIds = groups[item.channelUrl].orEmpty().sorted(),
            )
        }
    }

    private fun ResultRow.toItem(): SubscriptionItem = SubscriptionItem(
        channelUrl = this[SubscriptionsTable.channelUrl],
        name = this[SubscriptionsTable.name],
        avatarUrl = this[SubscriptionsTable.avatarUrl],
        subscribedAt = this[SubscriptionsTable.subscribedAt],
    )
}
