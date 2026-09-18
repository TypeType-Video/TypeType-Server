package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object NotificationStatesTable : Table("notification_states") {
    val userId = text("user_id")
    val subscriptionLastSeenUploaded = long("subscription_last_seen_uploaded")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}
