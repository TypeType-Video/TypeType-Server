package dev.typetype.server.models

import kotlinx.serialization.Serializable
import dev.typetype.server.services.HomeRecommendationSourceTag

@Serializable
data class HomeRecommendationPool(
    val subscriptions: List<VideoItem>,
    val discovery: List<VideoItem>,
    val subscriptionChannels: Set<String> = emptySet(),
    val sourceByUrl: Map<String, HomeRecommendationSourceTag> = emptyMap(),
    val sourceWeights: Map<HomeRecommendationSourceTag, Double> = emptyMap(),
)
