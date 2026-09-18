package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object BlockedChannelsTable : Table("blocked_channels") {
    val userId = text("user_id")
    val scope = text("scope").default("user")
    val channelUrl = text("channel_url")
    val channelName = text("name").nullable()
    val channelThumbnailUrl = text("thumbnail_url").nullable()
    val blockedAt = long("blocked_at")
    override val primaryKey = PrimaryKey(userId, channelUrl)
}
