package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionItem(
    val channelUrl: String,
    val name: String,
    val avatarUrl: String,
    val subscribedAt: Long = 0L,
)
