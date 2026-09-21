package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object SavedPlaylistsTable : Table("saved_playlists") {
    val id = text("id")
    val userId = text("user_id")
    val publicPlaylistId = text("public_playlist_id")
    val url = text("url")
    val title = text("title")
    val thumbnailUrl = text("thumbnail_url")
    val uploaderName = text("uploader_name")
    val streamCount = long("stream_count")
    val playlistType = text("playlist_type")
    val savedAt = long("saved_at")
    override val primaryKey = PrimaryKey(id)
}
