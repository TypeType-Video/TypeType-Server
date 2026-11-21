package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object TwitchAccountsTable : Table("twitch_accounts") {
    val userId = text("user_id")
    val encryptedAccessToken = text("encrypted_access_token")
    val encryptedRefreshToken = text("encrypted_refresh_token").nullable()
    val login = text("login")
    val displayName = text("display_name")
    val avatarUrl = text("avatar_url").default("")
    val expiresAt = long("expires_at").default(0)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}
