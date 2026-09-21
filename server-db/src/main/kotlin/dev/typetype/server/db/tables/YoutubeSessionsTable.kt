package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object YoutubeSessionsTable : Table("youtube_sessions") {
    val userId = text("user_id")
    val encryptedCookies = text("encrypted_cookies")
    val encryptedPoToken = text("encrypted_po_token")
    val authUser = integer("auth_user").default(0)
    val status = text("status")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val lastUsedAt = long("last_used_at").default(0)
    override val primaryKey = PrimaryKey(userId)
}
