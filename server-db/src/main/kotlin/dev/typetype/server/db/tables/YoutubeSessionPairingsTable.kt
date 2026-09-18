package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object YoutubeSessionPairingsTable : Table("youtube_session_pairings") {
    val code = text("code")
    val userId = text("user_id")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(code)
}
