package dev.typetype.server.services

import dev.typetype.server.db.tables.SavedPlaylistsTable
import dev.typetype.server.models.PublicPlaylistItem
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

fun UpdateBuilder<*>.writePlaylist(playlist: PublicPlaylistItem, savedAt: Long): Unit {
    this[SavedPlaylistsTable.publicPlaylistId] = playlist.id
    this[SavedPlaylistsTable.url] = playlist.url
    this[SavedPlaylistsTable.title] = playlist.title
    this[SavedPlaylistsTable.thumbnailUrl] = playlist.thumbnailUrl
    this[SavedPlaylistsTable.uploaderName] = playlist.uploaderName
    this[SavedPlaylistsTable.streamCount] = playlist.streamCount
    this[SavedPlaylistsTable.playlistType] = playlist.playlistType
    this[SavedPlaylistsTable.savedAt] = savedAt
}
