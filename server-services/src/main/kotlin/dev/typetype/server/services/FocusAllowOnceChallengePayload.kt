package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
internal data class FocusAllowOnceChallengePayload(
    val userId: String,
    val videoKey: String,
    val code: String,
    val expiresAt: Long,
)
