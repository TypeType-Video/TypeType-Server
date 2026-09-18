package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionFeedResponse(
    val videos: List<VideoItem>,
    val nextpage: String?,
    val generation: Long? = null,
    val generatedAt: Long? = null,
    val refreshing: Boolean = false,
)

@Serializable
data class SubscriptionFeedPreparingResponse(
    val code: String = "subscription_feed_preparing",
    val retryAfterMs: Long,
)
