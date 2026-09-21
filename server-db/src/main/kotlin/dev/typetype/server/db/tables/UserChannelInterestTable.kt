package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object UserChannelInterestTable : Table("user_channel_interest") {
    val userId = text("user_id")
    val uploaderUrl = text("uploader_url")
    val score = double("score")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId, uploaderUrl)
}
