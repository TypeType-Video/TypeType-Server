package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object RssFeedsTable : Table("rss_feeds") {
    val id = text("id")
    val userId = text("user_id")
    val name = text("name")
    val tokenHash = text("token_hash")
    val scope = text("scope")
    val includeVideos = bool("include_videos")
    val includeShorts = bool("include_shorts")
    val includeLive = bool("include_live")
    val includeUpcoming = bool("include_upcoming")
    val enabled = bool("enabled").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val lastUsedAt = long("last_used_at").nullable()

    init {
        index(false, userId)
        index(false, createdAt)
    }

    override val primaryKey = PrimaryKey(id)
}
