package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationsResponse

object HomeRecommendationPageBuilder {
    suspend fun build(
        args: HomeRecommendationApiArgs,
        mode: HomeRecommendationPoolMode,
        poolResolver: HomeRecommendationPoolResolver,
    ): HomeRecommendationsResponse {
        val cursor = if (mode == HomeRecommendationPoolMode.FULL) {
            args.cursor.withRotationSeed()
        } else {
            args.cursor
        }
        val resolvedPool = poolResolver.resolve(
            userId = args.userId,
            serviceId = args.serviceId,
            mode = mode,
            context = args.context,
        )
        val pool = if (mode == HomeRecommendationPoolMode.FULL && cursor.rotationSeed != 0L) {
            HomeRecommendationPoolRotation.apply(resolvedPool, cursor.rotationSeed)
        } else {
            resolvedPool
        }
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = cursor,
            limit = args.limit,
            context = args.context.sessionContext,
            sourceWeights = HomeRecommendationExploreBonus.apply(
                sourceWeights = pool.sourceWeights,
                pageIndex = HomeRecommendationCursorPageIndex.from(cursor, args.limit),
            ),
            mode = mode,
            userId = args.userId,
            serviceId = args.serviceId,
        )
        val finalPage = if (mode == HomeRecommendationPoolMode.SHORTS) {
            val refresh = HomeRecommendationShortsRefresher.refresh(
                pool = pool,
                page = page,
                cursor = cursor,
            )
            if (refresh.pool == pool && refresh.cursorOverride == null) {
                page
            } else {
                HomeRecommendationMixer.mix(
                    pool = refresh.pool,
                    cursor = refresh.cursorOverride ?: cursor,
                    limit = args.limit,
                    context = args.context.sessionContext,
                    sourceWeights = HomeRecommendationExploreBonus.apply(
                        sourceWeights = refresh.pool.sourceWeights,
                        pageIndex = HomeRecommendationCursorPageIndex.from(refresh.cursorOverride ?: cursor, args.limit),
                    ),
                    mode = mode,
                    userId = args.userId,
                    serviceId = args.serviceId,
                )
            }
        } else {
            page
        }
        return HomeRecommendationsResponse(
            items = finalPage.items,
            nextCursor = finalPage.nextCursor,
            hasMore = finalPage.nextCursor != null,
            debug = if (args.debug) {
                HomeRecommendationShortsDebugInfo.fromPage(finalPage)
            } else {
                null
            },
        )
    }

    private fun HomeRecommendationCursor.withRotationSeed(): HomeRecommendationCursor =
        if (rotationSeed != 0L || !isInitialPage()) {
            this
        } else {
            copy(rotationSeed = HomeRecommendationPoolRotation.newSeed())
        }

    private fun HomeRecommendationCursor.isInitialPage(): Boolean =
        subscriptionIndex == 0 && discoveryIndex == 0 && subscriptionRun == 0 && recentUrls.isEmpty()
}
