package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.AllowedPlaylistsTable
import dev.typetype.server.models.AllowedPlaylistItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class AllowedPlaylistsService {
    suspend fun getPlaylists(userId: String): List<AllowedPlaylistItem> = DatabaseFactory.query {
        AllowedPlaylistsTable.selectAll()
            .where { (AllowedPlaylistsTable.userId eq userId) or (AllowedPlaylistsTable.scope eq ALLOW_SCOPE_GLOBAL) }
            .orderBy(AllowedPlaylistsTable.allowedAt to SortOrder.DESC)
            .map(::toAllowedPlaylistItem)
    }

    suspend fun getGlobalPlaylists(): List<AllowedPlaylistItem> = DatabaseFactory.query {
        AllowedPlaylistsTable.selectAll()
            .where { AllowedPlaylistsTable.scope eq ALLOW_SCOPE_GLOBAL }
            .orderBy(AllowedPlaylistsTable.allowedAt to SortOrder.DESC)
            .map(::toAllowedPlaylistItem)
    }

    suspend fun getUserPlaylists(userId: String): List<AllowedPlaylistItem> = DatabaseFactory.query {
        AllowedPlaylistsTable.selectAll()
            .where { (AllowedPlaylistsTable.userId eq userId) and (AllowedPlaylistsTable.scope eq ALLOW_SCOPE_USER) }
            .orderBy(AllowedPlaylistsTable.allowedAt to SortOrder.DESC)
            .map(::toAllowedPlaylistItem)
    }

    suspend fun addPlaylist(userId: String, item: AllowedPlaylistItem, global: Boolean): AllowedPlaylistItem {
        val now = System.currentTimeMillis()
        val normalizedUrl = normalizePlaylistKey(item.url)
        DatabaseFactory.query {
            AllowedPlaylistsTable.deleteWhere { (playlistUrl eq normalizedUrl) and scopeClause(userId, global) }
            AllowedPlaylistsTable.insert {
                it[AllowedPlaylistsTable.userId] = userId
                it[scope] = if (global) ALLOW_SCOPE_GLOBAL else ALLOW_SCOPE_USER
                it[playlistUrl] = normalizedUrl
                it[title] = item.title
                it[thumbnailUrl] = item.thumbnailUrl
                it[uploaderName] = item.uploaderName
                it[allowedAt] = now
            }
        }
        return item.copy(url = normalizedUrl, allowedAt = now, global = global)
    }

    suspend fun deletePlaylist(userId: String, url: String, global: Boolean): Boolean = DatabaseFactory.query {
        AllowedPlaylistsTable.deleteWhere { (playlistUrl eq normalizePlaylistKey(url)) and scopeClause(userId, global) } > 0
    }

    private fun scopeClause(userId: String, global: Boolean) = if (global) {
        AllowedPlaylistsTable.scope eq ALLOW_SCOPE_GLOBAL
    } else {
        (AllowedPlaylistsTable.scope eq ALLOW_SCOPE_USER) and (AllowedPlaylistsTable.userId eq userId)
    }
}

private fun toAllowedPlaylistItem(row: ResultRow): AllowedPlaylistItem = AllowedPlaylistItem(
    url = row[AllowedPlaylistsTable.playlistUrl],
    title = row[AllowedPlaylistsTable.title],
    thumbnailUrl = row[AllowedPlaylistsTable.thumbnailUrl],
    uploaderName = row[AllowedPlaylistsTable.uploaderName],
    allowedAt = row[AllowedPlaylistsTable.allowedAt],
    global = row[AllowedPlaylistsTable.scope] == ALLOW_SCOPE_GLOBAL,
)
