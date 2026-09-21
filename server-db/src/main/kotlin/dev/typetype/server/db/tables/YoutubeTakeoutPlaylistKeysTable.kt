package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object YoutubeTakeoutPlaylistKeysTable : Table("youtube_takeout_playlist_keys") {
    val userId = text("user_id")
    val sourceKey = text("source_key")
    val playlistId = text("playlist_id")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId, sourceKey)
}
