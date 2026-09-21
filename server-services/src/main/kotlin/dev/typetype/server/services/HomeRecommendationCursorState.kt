package dev.typetype.server.services

data class HomeRecommendationCursorState(
    val subscriptionIndex: Int,
    val discoveryIndex: Int,
    val subscriptionRun: Int,
    val preferDiscovery: Boolean,
)
