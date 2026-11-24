package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusAllowOnceCancelResponse(
    val kind: String,
    val remainingToday: Int,
    val dailyLimit: Int,
)
