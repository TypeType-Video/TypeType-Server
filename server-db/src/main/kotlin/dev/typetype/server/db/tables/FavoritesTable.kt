package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object FavoritesTable : Table("favorites") {
    val userId = text("user_id")
    val videoUrl = text("video_url")
    val favoritedAt = long("favorited_at")
    val title = text("title").default("")
    val thumbnail = text("thumbnail").default("")
    val duration = long("duration").default(0L)
    val channelName = text("channel_name").default("")
    val channelUrl = text("channel_url").default("")
    val channelAvatar = text("channel_avatar").default("")
    val viewCount = long("view_count").default(0L)
    val publishedAt = long("published_at").default(-1L)
    override val primaryKey = PrimaryKey(userId, videoUrl)
}
