package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object PushNotificationDeliveriesTable : Table("push_notification_deliveries") {
    val eventId = text("event_id")
    val userId = text("user_id")
    val deviceId = text("device_id")
    val status = text("status")
    val attempts = integer("attempts")
    val lastError = text("last_error").nullable()
    val updatedAt = long("updated_at")

    init {
        index(false, userId, status)
        index(false, deviceId)
    }

    override val primaryKey = PrimaryKey(eventId, userId, deviceId)
}
