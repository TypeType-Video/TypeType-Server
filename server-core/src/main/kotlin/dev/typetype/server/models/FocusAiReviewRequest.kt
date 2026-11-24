package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusAiReviewRequest(
    val videoUrl: String,
    val reason: String,
    val timezone: String? = null,
)
