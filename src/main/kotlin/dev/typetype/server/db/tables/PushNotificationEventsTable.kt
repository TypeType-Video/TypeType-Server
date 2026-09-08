package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object PushNotificationEventsTable : Table("push_notification_events") {
    val eventId = text("event_id")
    val eventType = text("event_type")
    val instanceId = text("instance_id")
    val serviceId = integer("service_id")
    val channelId = text("channel_id")
    val videoId = text("video_id")
    val videoUrl = text("video_url")
    val title = text("title")
    val channelName = text("channel_name")
    val channelAvatarUrl = text("channel_avatar_url")
    val publishedAt = long("published_at")
    val createdAt = long("created_at")

    init {
        index(false, createdAt)
    }

    override val primaryKey = PrimaryKey(eventId)
}
