package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object AllowedChannelsTable : Table("allowed_channels") {
    val userId = text("user_id")
    val scope = text("scope").default("user")
    val channelUrl = text("channel_url")
    val channelName = text("name").nullable()
    val channelThumbnailUrl = text("thumbnail_url").nullable()
    val allowedAt = long("allowed_at")
    override val primaryKey = PrimaryKey(userId, channelUrl)
}
