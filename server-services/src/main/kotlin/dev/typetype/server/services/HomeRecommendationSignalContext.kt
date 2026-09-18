package dev.typetype.server.services

data class HomeRecommendationSignalContext(
    val userSubscriptions: List<String> = emptyList(),
    val historyItems: List<String> = emptyList(),
    val favoriteUrls: List<String> = emptyList(),
)
