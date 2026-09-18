package dev.typetype.server.services

enum class HomeRecommendationSourceTag {
    SUBSCRIPTION,
    DISCOVERY_TRENDING,
    DISCOVERY_THEME,
    DISCOVERY_EXPLORATION,
}

fun HomeRecommendationSourceTag.apiLabel(): String = when (this) {
    HomeRecommendationSourceTag.SUBSCRIPTION -> "subs"
    HomeRecommendationSourceTag.DISCOVERY_TRENDING -> "trending"
    HomeRecommendationSourceTag.DISCOVERY_THEME -> "theme"
    HomeRecommendationSourceTag.DISCOVERY_EXPLORATION -> "exploration"
}
