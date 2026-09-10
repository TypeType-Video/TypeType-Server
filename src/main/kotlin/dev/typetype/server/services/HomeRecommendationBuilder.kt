package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool

class HomeRecommendationBuilder(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionFeedService: SubscriptionFeedService,
    private val subscriptionShortsFeedService: SubscriptionShortsFeedService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val streamService: StreamService,
    private val trendingService: TrendingService,
) {
    suspend fun build(
        userId: String,
        serviceId: Int,
        mode: HomeRecommendationPoolMode,
        context: HomeRecommendationContext,
    ): HomeRecommendationPool {
        val signalService = HomeRecommendationUserSignalService(
            subscriptionsService = subscriptionsService,
            historyService = historyService,
            favoritesService = favoritesService,
            watchLaterService = watchLaterService,
            blockedService = blockedService,
        )
        val (profile, signalContext) = signalService.load(userId = userId)
        val candidates = HomeRecommendationCandidateService(
            subscriptionFeedService = subscriptionFeedService,
            subscriptionShortsFeedService = subscriptionShortsFeedService,
            streamService = streamService,
            trendingService = trendingService,
        )
        val candidatePool = candidates.fetchCandidates(
            userId = userId,
            serviceId = serviceId,
            profile = profile,
            mode = mode,
            signalContext = signalContext,
        )
        val pool = HomeRecommendationPoolBuilder().build(
            profile = profile,
            subscriptionCandidates = candidatePool.subscriptions,
            discoveryCandidates = candidatePool.discovery,
            context = context.sessionContext,
            mode = mode,
        )
        return pool
    }
}
