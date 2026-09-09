package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationsResponse

class HomeRecommendationService(
    private val poolResolver: HomeRecommendationPoolResolver,
) : AutoCloseable {
    private fun args(
        userId: String,
        serviceId: Int,
        limit: Int,
        cursor: HomeRecommendationCursor,
        context: HomeRecommendationContext,
        debug: Boolean,
    ): HomeRecommendationApiArgs = HomeRecommendationApiArgs(
        userId = userId,
        serviceId = serviceId,
        limit = limit,
        cursor = cursor,
        context = context,
        debug = debug,
    )

    suspend fun getHome(
        userId: String,
        serviceId: Int,
        limit: Int,
        cursor: HomeRecommendationCursor,
        context: HomeRecommendationContext,
        debug: Boolean = false,
    ): HomeRecommendationsResponse = HomeRecommendationPageBuilder.build(
        args = args(userId, serviceId, limit, cursor, context, debug),
        mode = HomeRecommendationPoolMode.FULL,
        poolResolver = poolResolver,
    )

    suspend fun getShorts(
        userId: String,
        serviceId: Int,
        limit: Int,
        cursor: HomeRecommendationCursor,
        context: HomeRecommendationContext,
        debug: Boolean = false,
    ): HomeRecommendationsResponse = HomeRecommendationPageBuilder.build(
        args = args(userId, serviceId, limit, cursor, context, debug),
        mode = HomeRecommendationPoolMode.SHORTS,
        poolResolver = poolResolver,
    )

    override fun close() {
        poolResolver.close()
    }
}
