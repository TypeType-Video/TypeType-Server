package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object BlockedKeywordsTable : Table("blocked_keywords") {
    val userId = text("user_id")
    val scope = text("scope").default("user")
    val keyword = text("keyword")
    val blockedAt = long("blocked_at")
    override val primaryKey = PrimaryKey(userId, keyword)
}
