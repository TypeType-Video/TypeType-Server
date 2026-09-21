package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class HomeRecommendationsDebug(
    val itemSources: Map<String, String>,
    val sourceBreakdown: Map<String, Int>,
    val discoveryRatio: Double,
    val targetDiscoveryRatio: Double,
    val discoveryFloorRatio: Double,
)
