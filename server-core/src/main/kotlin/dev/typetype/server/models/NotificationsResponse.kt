package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class NotificationsResponse(
    val items: List<NotificationItem>,
    val unreadCount: Int,
    val nextpage: String?,
    val available: Boolean = true,
)
