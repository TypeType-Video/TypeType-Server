package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.RssFeedsTable
import dev.typetype.server.db.tables.RssUserPoliciesTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AdminRssFeedItem
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class RssFeedAdminRepository {
    suspend fun list(page: Int, limit: Int): Pair<List<AdminRssFeedItem>, Long> = DatabaseFactory.query {
        val total = RssFeedsTable.selectAll().count()
        val rows = RssFeedsTable.selectAll()
            .orderBy(RssFeedsTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset((page - 1L) * limit)
            .toList()
        val selections = loadRssFeedSelections(rows.map { it[RssFeedsTable.id] })
        val feeds = rows.map { it.toStoredFeed(selections) }
        val userIds = feeds.map { it.userId }.distinct()
        val users = if (userIds.isEmpty()) emptyMap() else UsersTable.selectAll()
            .where { UsersTable.id inList userIds }
            .associateBy { it[UsersTable.id] }
        val policies = if (userIds.isEmpty()) emptyMap() else RssUserPoliciesTable.selectAll()
            .where { RssUserPoliciesTable.userId inList userIds }
            .associate { it[RssUserPoliciesTable.userId] to it[RssUserPoliciesTable.enabled] }
        feeds.mapNotNull { stored ->
            val user = users[stored.userId] ?: return@mapNotNull null
            AdminRssFeedItem(
                feed = stored.item,
                userId = stored.userId,
                userName = user[UsersTable.name],
                userEmail = user[UsersTable.email],
                userRssEnabled = policies[stored.userId] ?: true,
                userSuspended = user[UsersTable.suspended],
            )
        } to total
    }

    suspend fun userExists(userId: String): Boolean = DatabaseFactory.query {
        UsersTable.selectAll().where { UsersTable.id eq userId }.any()
    }
}
