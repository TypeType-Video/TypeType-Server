package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusAllowOnceVerifyResponse(
    val remainingToday: Int,
    val dailyLimit: Int,
    val expiresAt: Long,
)
