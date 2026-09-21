package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionGroupItem(
    val id: String,
    val name: String,
    val channelCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
