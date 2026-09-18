package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationFeedbackItem(
    val id: String,
    val feedbackType: String,
    val videoUrl: String? = null,
    val uploaderUrl: String? = null,
    val createdAt: Long,
)
