package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusAiReviewResponse(
    val status: String,
    val cooldownMs: Long? = null,
    val rejectionExpiresAt: Long? = null,
    val expiresAt: Long? = null,
)
