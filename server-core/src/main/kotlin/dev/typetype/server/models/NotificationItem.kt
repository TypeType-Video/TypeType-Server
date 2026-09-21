package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class NotificationItem(
    val id: String = "",
    val type: String,
    val title: String,
    val createdAt: Long,
    val publishedAt: Long = createdAt,
    val channelUrl: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val serviceId: Int,
    val serviceName: String,
    val read: Boolean = false,
    val video: VideoItem,
)
