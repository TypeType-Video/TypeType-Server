package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object UserSeekbarIconsTable : Table("user_seekbar_icons") {
    val userId = text("user_id")
    val mediaType = text("media_type")
    val content = binary("content")
    val version = text("version")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}
