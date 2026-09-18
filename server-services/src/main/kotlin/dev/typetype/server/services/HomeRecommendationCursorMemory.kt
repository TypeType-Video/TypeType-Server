package dev.typetype.server.services

data class HomeRecommendationCursorMemory(
    val recentChannels: List<String>,
    val recentSemanticKeys: List<String>,
    val creatorMomentum: Map<String, Int>,
    val creatorCooldownUntilMs: Map<String, Long>,
    val recentTopicPairs: List<String>,
    val recentUrls: List<String>,
)
