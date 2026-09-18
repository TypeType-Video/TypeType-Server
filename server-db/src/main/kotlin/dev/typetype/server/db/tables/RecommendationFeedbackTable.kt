package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object RecommendationFeedbackTable : Table("recommendation_feedback") {
    val id = text("id")
    val userId = text("user_id")
    val feedbackType = text("feedback_type")
    val videoUrl = text("video_url").nullable()
    val uploaderUrl = text("uploader_url").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
