package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class HomeRecommendationPage(
    val items: List<VideoItem>,
    val nextCursor: String?,
    val subscriptionCount: Int,
    val discoveryCount: Int,
    val targetDiscoveryRatio: Double,
    val discoveryFloorRatio: Double,
    val sourceByUrl: Map<String, HomeRecommendationSourceTag>,
)
