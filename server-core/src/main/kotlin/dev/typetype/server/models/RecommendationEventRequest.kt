package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationEventRequest(
    val eventType: String,
    val serviceId: Int? = null,
    val intent: String? = null,
    val videoUrl: String? = null,
    val uploaderUrl: String? = null,
    val title: String? = null,
    val watchRatio: Double? = null,
    val watchDurationMs: Long? = null,
    val contextKey: String? = null,
)
