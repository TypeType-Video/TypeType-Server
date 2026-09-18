package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object RecommendationFeedHistoryTable : Table("recommendation_feed_history") {
    val userId = text("user_id")
    val videoUrl = text("video_url")
    val showCount = integer("show_count")
    val lastShown = long("last_shown")
    override val primaryKey = PrimaryKey(userId, videoUrl)
}
