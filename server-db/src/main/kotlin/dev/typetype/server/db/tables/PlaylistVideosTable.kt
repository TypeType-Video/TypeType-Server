package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object PlaylistVideosTable : Table("playlist_videos") {
    val id = text("id")
    val playlistId = text("playlist_id").references(PlaylistsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = text("user_id")
    val url = text("url")
    val title = text("title")
    val thumbnail = text("thumbnail")
    val duration = long("duration")
    val position = integer("position")
    val channelName = text("channel_name").default("")
    val channelUrl = text("channel_url").default("")
    val channelAvatar = text("channel_avatar").default("")
    val viewCount = long("view_count").default(0L)
    val addedAt = long("added_at").default(0L)
    val publishedAt = long("published_at").default(-1L)
    override val primaryKey = PrimaryKey(id)
}
