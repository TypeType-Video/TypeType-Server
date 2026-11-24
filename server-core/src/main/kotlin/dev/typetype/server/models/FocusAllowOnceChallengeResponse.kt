package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusAllowOnceChallengeResponse(
    val challengeId: String,
    val challengeText: String,
    val expiresAt: Long,
    val remainingToday: Int,
    val dailyLimit: Int,
    val kind: String = "outside_group",
    val cooldownMs: Long = 0L,
)
