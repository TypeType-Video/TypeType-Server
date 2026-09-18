package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SavedPlaylistsTable
import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.SavedPlaylistItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class SavedPlaylistService {
    suspend fun getAll(userId: String): List<SavedPlaylistItem> = DatabaseFactory.query {
        SavedPlaylistsTable.selectAll()
            .where { SavedPlaylistsTable.userId eq userId }
            .orderBy(SavedPlaylistsTable.savedAt to SortOrder.DESC)
            .map { it.toSavedPlaylistItem() }
    }

    suspend fun save(userId: String, playlist: PublicPlaylistItem): SavedPlaylistItem = DatabaseFactory.query {
        val existing = SavedPlaylistsTable.selectAll()
            .where { (SavedPlaylistsTable.userId eq userId) and (SavedPlaylistsTable.url eq playlist.url) }
            .singleOrNull()
        val id = existing?.get(SavedPlaylistsTable.id) ?: UUID.randomUUID().toString()
        val savedAt = existing?.get(SavedPlaylistsTable.savedAt) ?: System.currentTimeMillis()
        if (existing == null) {
            SavedPlaylistsTable.insert {
                it[SavedPlaylistsTable.id] = id
                it[SavedPlaylistsTable.userId] = userId
                it.writePlaylist(playlist, savedAt)
            }
        } else {
            SavedPlaylistsTable.update({ SavedPlaylistsTable.id eq id }) {
                it.writePlaylist(playlist, savedAt)
            }
        }
        playlist.toSavedPlaylistItem(id = id, savedAt = savedAt)
    }

    suspend fun delete(userId: String, id: String): Boolean = DatabaseFactory.query {
        SavedPlaylistsTable.deleteWhere { (SavedPlaylistsTable.id eq id) and (SavedPlaylistsTable.userId eq userId) } > 0
    }

    private fun ResultRow.toSavedPlaylistItem(): SavedPlaylistItem = SavedPlaylistItem(
        id = this[SavedPlaylistsTable.id],
        publicPlaylistId = this[SavedPlaylistsTable.publicPlaylistId],
        url = this[SavedPlaylistsTable.url],
        title = this[SavedPlaylistsTable.title],
        thumbnailUrl = this[SavedPlaylistsTable.thumbnailUrl],
        uploaderName = this[SavedPlaylistsTable.uploaderName],
        streamCount = this[SavedPlaylistsTable.streamCount],
        playlistType = this[SavedPlaylistsTable.playlistType],
        savedAt = this[SavedPlaylistsTable.savedAt],
    )

    private fun PublicPlaylistItem.toSavedPlaylistItem(id: String, savedAt: Long): SavedPlaylistItem =
        SavedPlaylistItem(
            id = id,
            publicPlaylistId = this.id,
            url = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            uploaderName = uploaderName,
            streamCount = streamCount,
            playlistType = playlistType,
            savedAt = savedAt,
        )
}
