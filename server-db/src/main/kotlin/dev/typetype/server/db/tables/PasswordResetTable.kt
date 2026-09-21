package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object PasswordResetTable : Table("password_resets") {
    val id = text("id")
    val userId = text("user_id")
    val token = text("token").uniqueIndex()
    val expiresAt = long("expires_at")
    val usedAt = long("used_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
