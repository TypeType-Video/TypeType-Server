package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object ProfileAccountsTable : Table("profile_accounts") {
    val profileId = text("profile_id")
    val ownerUserId = text("owner_user_id")
    val displayName = text("display_name")
    val isDefault = bool("is_default")
    val lastUsedAt = long("last_used_at")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    init {
        index(false, ownerUserId)
        index(false, ownerUserId, isDefault)
    }

    override val primaryKey = PrimaryKey(profileId)
}
