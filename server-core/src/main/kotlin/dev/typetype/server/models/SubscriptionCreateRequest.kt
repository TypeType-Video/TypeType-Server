package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionCreateRequest(
    val channelUrl: String,
    val name: String,
    val avatarUrl: String,
)
