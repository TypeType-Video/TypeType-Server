package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object FocusAllowOnceGrantsTable : Table("focus_allow_once_grants") {
    val id = text("id")
    val userId = text("user_id")
    val videoKey = text("video_key")
    val dayKey = text("day_key")
    val grantKind = text("grant_kind").default("outside_group")
    val grantSource = text("grant_source").default("captcha")
    val grantedAt = long("granted_at")
    val unlockAt = long("unlock_at").default(0L)
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(id)
}
