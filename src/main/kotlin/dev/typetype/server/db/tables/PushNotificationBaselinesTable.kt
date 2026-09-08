package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object PushNotificationBaselinesTable : Table("push_notification_baselines") {
    val userId = text("user_id")
    val initializedAt = long("initialized_at")

    override val primaryKey = PrimaryKey(userId)
}
