package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object RecommendationEventsTable : Table("recommendation_events") {
    val id = text("id")
    val userId = text("user_id")
    val eventType = text("event_type")
    val videoUrl = text("video_url").nullable()
    val uploaderUrl = text("uploader_url").nullable()
    val title = text("title").nullable()
    val watchRatio = double("watch_ratio").nullable()
    val watchDurationMs = long("watch_duration_ms").nullable()
    val contextKey = text("context_key").nullable()
    val occurredAt = long("occurred_at")
    override val primaryKey = PrimaryKey(id)
}
