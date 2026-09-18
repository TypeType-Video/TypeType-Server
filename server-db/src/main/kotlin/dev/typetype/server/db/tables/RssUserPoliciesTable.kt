package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object RssUserPoliciesTable : Table("rss_user_policies") {
    val userId = text("user_id")
    val enabled = bool("enabled").default(true)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}
