package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class UnreadCountResponse(
    val unreadCount: Int,
    val available: Boolean = true,
)
