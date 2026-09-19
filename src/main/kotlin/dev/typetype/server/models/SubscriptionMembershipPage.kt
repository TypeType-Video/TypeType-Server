package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionMembershipPage(
    val items: List<SubscriptionGroupMembershipItem>,
    val total: Long,
    val totalSubscriptions: Long,
    val ungroupedCount: Long,
    val page: Int,
    val limit: Int,
)
