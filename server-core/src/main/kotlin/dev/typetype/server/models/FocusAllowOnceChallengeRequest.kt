package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusAllowOnceChallengeRequest(
    val videoUrl: String,
    val timezone: String? = null,
)
