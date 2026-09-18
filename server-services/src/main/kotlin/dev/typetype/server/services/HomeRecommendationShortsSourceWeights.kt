package dev.typetype.server.services

object HomeRecommendationShortsSourceWeights {
    fun forProfile(profile: HomeRecommendationProfile): Map<HomeRecommendationSourceTag, Double> {
        if (!profile.personalizationEnabled) return emptyMap()
        val hasHistory = profile.keywordAffinity.isNotEmpty() || profile.topicInterest.isNotEmpty()
        val hasSubs = profile.subscriptionChannels.isNotEmpty()
        val subscriptionWeight = if (hasSubs && hasHistory) 1.18 else if (hasSubs) 1.10 else 1.0
        val discoveryWeight = if (hasHistory) 0.92 else 0.98
        return mapOf(
            HomeRecommendationSourceTag.SUBSCRIPTION to subscriptionWeight,
            HomeRecommendationSourceTag.DISCOVERY_TRENDING to discoveryWeight,
            HomeRecommendationSourceTag.DISCOVERY_THEME to discoveryWeight,
            HomeRecommendationSourceTag.DISCOVERY_EXPLORATION to discoveryWeight,
        )
    }
}
