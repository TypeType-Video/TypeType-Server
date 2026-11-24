package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object FocusAiReviewsTable : Table("focus_ai_reviews") {
    val id = text("id")
    val userId = text("user_id")
    val videoKey = text("video_key")
    val kind = text("kind")
    val score = integer("score")
    val cooldownMs = long("cooldown_ms")
    val reviewedAt = long("reviewed_at")
    val rejectionExpiresAt = long("rejection_expires_at")
    override val primaryKey = PrimaryKey(id)
}
