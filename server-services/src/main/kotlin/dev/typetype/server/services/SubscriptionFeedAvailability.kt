package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class SubscriptionFeedAvailability(
    val videos: List<VideoItem>,
    val available: Boolean,
)

data class SubscriptionFeedWithSources(
    val videos: List<VideoItem>,
    val available: Boolean,
    val sourceChannelUrls: Map<String, List<String>>,
)
