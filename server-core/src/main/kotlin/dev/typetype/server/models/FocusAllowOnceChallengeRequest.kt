package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusAllowOnceChallengeRequest(
    val videoUrl: String,
    val kind: String = "outside_group",
    val timezone: String? = null,
)
