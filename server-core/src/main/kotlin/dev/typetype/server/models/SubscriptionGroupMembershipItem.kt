package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionGroupMembershipItem(
    val channelUrl: String,
    val name: String,
    val avatarUrl: String,
    val subscribedAt: Long,
    val groupIds: List<String>,
)
