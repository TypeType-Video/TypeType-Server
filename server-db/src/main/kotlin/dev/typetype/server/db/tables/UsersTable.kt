package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object UsersTable : Table("users") {
    val id = text("id")
    val email = text("email").uniqueIndex()
    val passwordHash = text("password_hash")
    val name = text("name")
    val role = text("role") // "admin", "moderator", "user"
    val avatarUrl = text("avatar_url").nullable()
    val avatarType = text("avatar_type").nullable()
    val avatarCode = text("avatar_code").nullable()
    val publicUsername = text("public_username").nullable().uniqueIndex()
    val bio = text("bio").nullable()
    val oidcIssuer = text("oidc_issuer").nullable()
    val oidcSubject = text("oidc_subject").nullable()
    val verified = bool("verified").default(false)
    val suspended = bool("suspended").default(false)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}
