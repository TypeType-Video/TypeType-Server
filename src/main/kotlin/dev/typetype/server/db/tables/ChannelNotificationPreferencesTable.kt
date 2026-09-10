package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object ChannelNotificationPreferencesTable : Table("channel_notification_preferences") {
    val userId = text("user_id")
    val channelUrl = text("channel_url")
    val enabled = bool("enabled")
    val updatedAt = long("updated_at")

    init {
        index(false, userId)
    }

    override val primaryKey = PrimaryKey(userId, channelUrl)
}
