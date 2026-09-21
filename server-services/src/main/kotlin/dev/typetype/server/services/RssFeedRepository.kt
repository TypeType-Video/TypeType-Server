package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.RssFeedChannelsTable
import dev.typetype.server.db.tables.RssFeedServicesTable
import dev.typetype.server.db.tables.RssFeedsTable
import dev.typetype.server.db.tables.RssUserPoliciesTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.RssFeedItem
import dev.typetype.server.models.RssFeedRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update

data class StoredRssFeed(val item: RssFeedItem, val userId: String, val tokenHash: String)

class RssFeedRepository {
    suspend fun list(userId: String): List<RssFeedItem> = DatabaseFactory.query {
        val rows = RssFeedsTable.selectAll().where { RssFeedsTable.userId eq userId }
            .orderBy(RssFeedsTable.createdAt to SortOrder.DESC)
            .toList()
        val selections = loadRssFeedSelections(rows.map { it[RssFeedsTable.id] })
        rows.map { it.toStoredFeed(selections).item }
    }

    suspend fun find(feedId: String): StoredRssFeed? = DatabaseFactory.query {
        RssFeedsTable.selectAll().where { RssFeedsTable.id eq feedId }.singleOrNull()?.toStoredFeed()
    }

    suspend fun createWithinLimit(
        userId: String,
        id: String,
        tokenHash: String,
        request: RssFeedRequest,
        limit: Int,
    ): RssFeedItem? =
        DatabaseFactory.query {
            TransactionManager.current().exec("SELECT pg_advisory_xact_lock(${userId.hashCode().toLong()})")
            val count = RssFeedsTable.selectAll().where { RssFeedsTable.userId eq userId }.count()
            if (count >= limit) return@query null
            val now = System.currentTimeMillis()
            RssFeedsTable.insert {
                it[RssFeedsTable.id] = id
                it[RssFeedsTable.userId] = userId
                it[name] = request.name
                it[RssFeedsTable.tokenHash] = tokenHash
                it[scope] = request.scope
                it[includeVideos] = request.includeVideos
                it[includeShorts] = request.includeShorts
                it[includeLive] = request.includeLive
                it[includeUpcoming] = request.includeUpcoming
                it[enabled] = true
                it[createdAt] = now
                it[updatedAt] = now
            }
            replaceSelections(id, request)
            RssFeedsTable.selectAll().where { RssFeedsTable.id eq id }.single().toStoredFeed().item
        }

    suspend fun update(userId: String, feedId: String, request: RssFeedRequest): RssFeedItem? =
        DatabaseFactory.query {
            val changed = RssFeedsTable.update({
                (RssFeedsTable.id eq feedId) and (RssFeedsTable.userId eq userId)
            }) {
                it[name] = request.name
                it[scope] = request.scope
                it[includeVideos] = request.includeVideos
                it[includeShorts] = request.includeShorts
                it[includeLive] = request.includeLive
                it[includeUpcoming] = request.includeUpcoming
                it[updatedAt] = System.currentTimeMillis()
            }
            if (changed == 0) return@query null
            replaceSelections(feedId, request)
            RssFeedsTable.selectAll().where { RssFeedsTable.id eq feedId }.single().toStoredFeed().item
        }

    suspend fun replaceToken(userId: String, feedId: String, tokenHash: String): RssFeedItem? =
        DatabaseFactory.query {
            val changed = RssFeedsTable.update({
                (RssFeedsTable.id eq feedId) and (RssFeedsTable.userId eq userId)
            }) {
                it[RssFeedsTable.tokenHash] = tokenHash
                it[updatedAt] = System.currentTimeMillis()
            }
            if (changed == 0) null else RssFeedsTable.selectAll()
                .where { RssFeedsTable.id eq feedId }.single().toStoredFeed().item
        }

    suspend fun setEnabled(userId: String, feedId: String, enabled: Boolean): RssFeedItem? =
        DatabaseFactory.query {
            val changed = RssFeedsTable.update({
                (RssFeedsTable.id eq feedId) and (RssFeedsTable.userId eq userId)
            }) {
                it[RssFeedsTable.enabled] = enabled
                it[updatedAt] = System.currentTimeMillis()
            }
            if (changed == 0) null else RssFeedsTable.selectAll()
                .where { RssFeedsTable.id eq feedId }.single().toStoredFeed().item
        }

    suspend fun setEnabledByAdmin(feedId: String, enabled: Boolean): RssFeedItem? = DatabaseFactory.query {
        val changed = RssFeedsTable.update({ RssFeedsTable.id eq feedId }) {
            it[RssFeedsTable.enabled] = enabled
            it[updatedAt] = System.currentTimeMillis()
        }
        if (changed == 0) null else RssFeedsTable.selectAll()
            .where { RssFeedsTable.id eq feedId }.single().toStoredFeed().item
    }

    suspend fun delete(userId: String, feedId: String): Boolean = DatabaseFactory.query {
        val owned = RssFeedsTable.selectAll().where {
            (RssFeedsTable.id eq feedId) and (RssFeedsTable.userId eq userId)
        }.count() > 0
        if (!owned) return@query false
        deleteFeed(feedId)
        true
    }

    suspend fun deleteByAdmin(feedId: String): Boolean = DatabaseFactory.query {
        val exists = RssFeedsTable.selectAll().where { RssFeedsTable.id eq feedId }.count() > 0
        if (!exists) return@query false
        deleteFeed(feedId)
        true
    }

    suspend fun touch(feedId: String, timestamp: Long) = DatabaseFactory.query {
        RssFeedsTable.update({ RssFeedsTable.id eq feedId }) { it[lastUsedAt] = timestamp }
    }

    suspend fun userEnabled(userId: String): Boolean = DatabaseFactory.query {
        val active = UsersTable.selectAll().where { UsersTable.id eq userId }
            .singleOrNull()?.get(UsersTable.suspended) == false
        if (!active) return@query false
        RssUserPoliciesTable.selectAll().where { RssUserPoliciesTable.userId eq userId }
            .singleOrNull()?.get(RssUserPoliciesTable.enabled) ?: true
    }

    suspend fun setUserEnabled(userId: String, enabled: Boolean) = DatabaseFactory.query {
        val changed = RssUserPoliciesTable.update({ RssUserPoliciesTable.userId eq userId }) {
            it[RssUserPoliciesTable.enabled] = enabled
            it[updatedAt] = System.currentTimeMillis()
        }
        if (changed == 0) RssUserPoliciesTable.insert {
            it[RssUserPoliciesTable.userId] = userId
            it[RssUserPoliciesTable.enabled] = enabled
            it[updatedAt] = System.currentTimeMillis()
        }
    }

    private fun replaceSelections(feedId: String, request: RssFeedRequest) {
        RssFeedChannelsTable.deleteWhere { RssFeedChannelsTable.feedId eq feedId }
        request.channelUrls.forEach { url ->
            RssFeedChannelsTable.insert {
                it[RssFeedChannelsTable.feedId] = feedId
                it[channelUrl] = url
            }
        }
        RssFeedServicesTable.deleteWhere { RssFeedServicesTable.feedId eq feedId }
        request.serviceIds.forEach { service ->
            RssFeedServicesTable.insert {
                it[RssFeedServicesTable.feedId] = feedId
                it[serviceId] = service
            }
        }
    }

    private fun deleteFeed(feedId: String) {
        RssFeedChannelsTable.deleteWhere { RssFeedChannelsTable.feedId eq feedId }
        RssFeedServicesTable.deleteWhere { RssFeedServicesTable.feedId eq feedId }
        RssFeedsTable.deleteWhere { RssFeedsTable.id eq feedId }
    }
}
