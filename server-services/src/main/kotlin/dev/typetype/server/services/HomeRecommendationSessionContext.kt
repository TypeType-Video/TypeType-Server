package dev.typetype.server.services

data class HomeRecommendationSessionContext(
    val intent: HomeRecommendationSessionIntent,
    val deviceClass: HomeRecommendationDeviceClass,
)
