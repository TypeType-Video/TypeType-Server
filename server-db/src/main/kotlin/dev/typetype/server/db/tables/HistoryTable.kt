package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object HistoryTable : Table("history") {
    val id = text("id")
    val userId = text("user_id")
    val url = text("url")
    val title = text("title")
    val thumbnail = text("thumbnail")
    val channelName = text("channel_name")
    val channelUrl = text("channel_url")
    val channelAvatar = text("channel_avatar").default("")
    val duration = long("duration")
    val progress = long("progress")
    val watchedAt = long("watched_at")
    override val primaryKey = PrimaryKey(id)
}
