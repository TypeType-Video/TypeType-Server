package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class MarkNotificationsReadResponse(
    val readAt: Long,
    val unreadCount: Int,
    val available: Boolean = true,
)
