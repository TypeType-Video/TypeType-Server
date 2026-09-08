package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class SubscriptionFeedAvailability(
    val videos: List<VideoItem>,
    val available: Boolean,
)

internal data class SubscriptionFeedWithSources(
    val videos: List<VideoItem>,
    val available: Boolean,
    val sourceChannelUrls: Map<String, List<String>>,
)
