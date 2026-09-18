package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object PushNotificationSeenVideosTable : Table("push_notification_seen_videos") {
    val userId = text("user_id")
    val serviceId = integer("service_id")
    val channelId = text("channel_id")
    val videoId = text("video_id")
    val firstSeenAt = long("first_seen_at")

    init {
        index(false, userId)
    }

    override val primaryKey = PrimaryKey(userId, serviceId, channelId, videoId)
}
