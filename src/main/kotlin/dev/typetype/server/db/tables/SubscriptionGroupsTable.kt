package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object SubscriptionGroupsTable : Table("subscription_groups") {
    val id = text("id")
    val userId = text("user_id")
    val name = text("name")
    val normalizedName = text("normalized_name")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    init {
        uniqueIndex(userId, normalizedName)
    }

    override val primaryKey = PrimaryKey(id)
}
