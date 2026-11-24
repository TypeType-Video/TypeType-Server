package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object FocusAllowOnceGrantsTable : Table("focus_allow_once_grants") {
    val id = text("id")
    val userId = text("user_id")
    val videoKey = text("video_key")
    val dayKey = text("day_key")
    val grantedAt = long("granted_at")
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(id)
}
