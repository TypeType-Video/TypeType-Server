package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationFeedbackRequest(
    val feedbackType: String,
    val videoUrl: String? = null,
    val uploaderUrl: String? = null,
)
