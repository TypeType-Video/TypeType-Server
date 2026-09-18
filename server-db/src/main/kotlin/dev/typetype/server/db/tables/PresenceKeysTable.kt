package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object PresenceKeysTable : Table("presence_keys") {
    val id = text("id")
    val userId = text("user_id")
    val name = text("name")
    val tokenPrefix = text("token_prefix")
    val tokenHash = text("token_hash").uniqueIndex()
    val createdAt = long("created_at")
    val lastUsedAt = long("last_used_at").nullable()

    init {
        index(false, userId)
    }

    override val primaryKey = PrimaryKey(id)
}
