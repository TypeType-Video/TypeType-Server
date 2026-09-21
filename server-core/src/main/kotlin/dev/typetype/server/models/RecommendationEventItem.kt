package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationEventItem(
    val id: String,
    val eventType: String,
    val videoUrl: String? = null,
    val uploaderUrl: String? = null,
    val title: String? = null,
    val watchRatio: Double? = null,
    val watchDurationMs: Long? = null,
    val contextKey: String? = null,
    val occurredAt: Long,
    val publishedAt: Long = occurredAt,
)
