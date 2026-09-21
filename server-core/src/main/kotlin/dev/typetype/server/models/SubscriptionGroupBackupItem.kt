package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionGroupBackupItem(
    val name: String,
    val channelUrls: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)
