package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.AllowedChannelsTable
import dev.typetype.server.models.AllowedChannelItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class AllowedChannelsService {
    suspend fun getChannels(userId: String): List<AllowedChannelItem> = DatabaseFactory.query {
        AllowedChannelsTable.selectAll()
            .where { (AllowedChannelsTable.userId eq userId) or (AllowedChannelsTable.scope eq ALLOW_SCOPE_GLOBAL) }
            .orderBy(AllowedChannelsTable.allowedAt to SortOrder.DESC)
            .map(::toAllowedChannelItem)
    }

    suspend fun getGlobalChannels(): List<AllowedChannelItem> = DatabaseFactory.query {
        AllowedChannelsTable.selectAll()
            .where { AllowedChannelsTable.scope eq ALLOW_SCOPE_GLOBAL }
            .orderBy(AllowedChannelsTable.allowedAt to SortOrder.DESC)
            .map(::toAllowedChannelItem)
    }

    suspend fun getUserChannels(userId: String): List<AllowedChannelItem> = DatabaseFactory.query {
        AllowedChannelsTable.selectAll()
            .where { (AllowedChannelsTable.userId eq userId) and (AllowedChannelsTable.scope eq ALLOW_SCOPE_USER) }
            .orderBy(AllowedChannelsTable.allowedAt to SortOrder.DESC)
            .map(::toAllowedChannelItem)
    }

    suspend fun addChannel(
        userId: String,
        url: String,
        name: String? = null,
        thumbnailUrl: String? = null,
        global: Boolean = false,
    ): AllowedChannelItem {
        val now = System.currentTimeMillis()
        val normalizedUrl = normalizeChannelKey(url)
        DatabaseFactory.query {
            val scopeClause = if (global) {
                AllowedChannelsTable.scope eq ALLOW_SCOPE_GLOBAL
            } else {
                (AllowedChannelsTable.scope eq ALLOW_SCOPE_USER) and (AllowedChannelsTable.userId eq userId)
            }
            AllowedChannelsTable.deleteWhere { (channelUrl eq normalizedUrl) and scopeClause }
            AllowedChannelsTable.insert {
                it[AllowedChannelsTable.userId] = userId
                it[AllowedChannelsTable.scope] = if (global) ALLOW_SCOPE_GLOBAL else ALLOW_SCOPE_USER
                it[channelUrl] = normalizedUrl
                it[channelName] = name
                it[channelThumbnailUrl] = thumbnailUrl
                it[allowedAt] = now
            }
        }
        return AllowedChannelItem(url = normalizedUrl, name = name, thumbnailUrl = thumbnailUrl, allowedAt = now, global = global)
    }

    suspend fun deleteChannel(userId: String, url: String, role: String): Boolean = DatabaseFactory.query {
        val canDeleteGlobal = role == "admin" || role == "moderator"
        val scopeClause = if (canDeleteGlobal) {
            (AllowedChannelsTable.scope eq ALLOW_SCOPE_GLOBAL) or (AllowedChannelsTable.userId eq userId)
        } else {
            (AllowedChannelsTable.scope eq ALLOW_SCOPE_USER) and (AllowedChannelsTable.userId eq userId)
        }
        AllowedChannelsTable.deleteWhere { (AllowedChannelsTable.channelUrl eq normalizeChannelKey(url)) and scopeClause } > 0
    }
}

private fun toAllowedChannelItem(row: ResultRow): AllowedChannelItem = AllowedChannelItem(
    url = row[AllowedChannelsTable.channelUrl],
    name = row[AllowedChannelsTable.channelName],
    thumbnailUrl = row[AllowedChannelsTable.channelThumbnailUrl],
    allowedAt = row[AllowedChannelsTable.allowedAt],
    global = row[AllowedChannelsTable.scope] == ALLOW_SCOPE_GLOBAL,
)
