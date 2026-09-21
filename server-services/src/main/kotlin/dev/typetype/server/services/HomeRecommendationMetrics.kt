package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
data class HomeRecommendationMetrics(
    val ndcgAt10: Double,
    val diversityAt10: Double,
    val duplicateRateAt10: Double,
)
