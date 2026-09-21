package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object SessionsTable : Table("sessions") {
    val id = text("id")
    val userId = text("user_id")
    val token = text("token").uniqueIndex()
    val refreshTokenHash = text("refresh_token_hash").nullable()
    val expiresAt = long("expires_at")
    val createdAt = long("created_at").default(0)
    val revokedAt = long("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
