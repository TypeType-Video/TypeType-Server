package dev.typetype.server.services

data class HomeRecommendationApiArgs(
    val userId: String,
    val serviceId: Int,
    val limit: Int,
    val cursor: HomeRecommendationCursor,
    val context: HomeRecommendationContext,
    val debug: Boolean = false,
)
