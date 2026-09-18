package dev.typetype.server.services

data class HomeRecommendationTargetPlan(
    val targetSubscription: Int,
    val targetDiscovery: Int,
    val noveltyBudget: Int,
    val discoveryFloor: Int,
    val targetDiscoveryRatio: Double,
    val discoveryFloorRatio: Double,
)
