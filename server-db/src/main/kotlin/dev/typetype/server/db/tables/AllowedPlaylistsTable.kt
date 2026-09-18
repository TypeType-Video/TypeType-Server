package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object AllowedPlaylistsTable : Table("allowed_playlists") {
    val userId = text("user_id")
    val scope = text("scope").default("user")
    val playlistUrl = text("playlist_url")
    val title = text("title").nullable()
    val thumbnailUrl = text("thumbnail_url").nullable()
    val uploaderName = text("uploader_name").nullable()
    val allowedAt = long("allowed_at")
}
