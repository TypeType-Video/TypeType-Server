package dev.typetype.server.services

data class HomeRecommendationContext(
    val serviceId: Int,
    val sessionContext: HomeRecommendationSessionContext,
)
