package dev.typetype.server.services

data class HomeRecommendationDecision(
    val forceDiscovery: Boolean,
    val wantDiscovery: Boolean,
    val target: HomeRecommendationTargetPlan,
    val forceNovelty: Boolean,
)
