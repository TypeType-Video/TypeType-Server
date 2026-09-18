package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object WatchLaterTable : Table("watch_later") {
    val userId = text("user_id")
    val url = text("url")
    val title = text("title")
    val thumbnail = text("thumbnail")
    val duration = long("duration")
    val addedAt = long("added_at")
    val channelName = text("channel_name").default("")
    val channelUrl = text("channel_url").default("")
    val channelAvatar = text("channel_avatar").default("")
    val viewCount = long("view_count").default(0L)
    val publishedAt = long("published_at").default(-1L)
    override val primaryKey = PrimaryKey(userId, url)
}
