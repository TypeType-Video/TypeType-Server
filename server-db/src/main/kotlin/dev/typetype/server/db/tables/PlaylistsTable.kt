package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object PlaylistsTable : Table("playlists") {
    val id = text("id")
    val userId = text("user_id")
    val name = text("name")
    val description = text("description")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
