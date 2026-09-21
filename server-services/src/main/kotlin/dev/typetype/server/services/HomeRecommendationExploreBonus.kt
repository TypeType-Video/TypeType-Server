package dev.typetype.server.services

object HomeRecommendationExploreBonus {
    fun apply(
        sourceWeights: Map<HomeRecommendationSourceTag, Double>,
        pageIndex: Int,
    ): Map<HomeRecommendationSourceTag, Double> {
        if (sourceWeights.isEmpty()) return sourceWeights
        val bonus = when {
            pageIndex <= 0 -> 0.12
            pageIndex <= 2 -> 0.06
            else -> 0.02
        }
        return sourceWeights + mapOf(
            HomeRecommendationSourceTag.DISCOVERY_EXPLORATION to
                ((sourceWeights[HomeRecommendationSourceTag.DISCOVERY_EXPLORATION] ?: 1.0) + bonus),
        )
    }
}
