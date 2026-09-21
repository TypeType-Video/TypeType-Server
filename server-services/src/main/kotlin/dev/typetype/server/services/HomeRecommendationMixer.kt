package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool

object HomeRecommendationMixer {
    fun mix(
        pool: HomeRecommendationPool,
        cursor: HomeRecommendationCursor,
        limit: Int,
        context: HomeRecommendationSessionContext,
        sourceWeights: Map<HomeRecommendationSourceTag, Double> = emptyMap(),
        mode: HomeRecommendationPoolMode = HomeRecommendationPoolMode.FULL,
        userId: String? = null,
        serviceId: Int? = null,
    ): HomeRecommendationPage = HomeRecommendationMixEngine.mix(
        pool = pool,
        cursor = cursor,
        limit = limit,
        context = context,
        sourceWeights = sourceWeights,
        mode = mode,
        userId = userId,
        serviceId = serviceId,
    )
}
