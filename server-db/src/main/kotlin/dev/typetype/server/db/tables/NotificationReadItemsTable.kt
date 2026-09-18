package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object NotificationReadItemsTable : Table("notification_read_items") {
    val userId = text("user_id")
    val notificationId = text("notification_id")
    val readAt = long("read_at")

    init {
        index(false, userId, readAt)
    }

    override val primaryKey = PrimaryKey(userId, notificationId)
}
