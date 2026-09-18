package dev.typetype.server.services

object HomeRecommendationPoolWeights {
    fun forMode(profile: HomeRecommendationProfile, shortsMode: Boolean): Map<HomeRecommendationSourceTag, Double> {
        if (shortsMode) return HomeRecommendationShortsSourceWeights.forProfile(profile)
        return emptyMap()
    }
}
