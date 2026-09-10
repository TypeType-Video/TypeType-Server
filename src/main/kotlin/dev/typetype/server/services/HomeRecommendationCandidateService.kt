package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult

import dev.typetype.server.models.VideoItem

class HomeRecommendationCandidateService(
    private val subscriptionFeedService: SubscriptionFeedService,
    private val subscriptionShortsFeedService: SubscriptionShortsFeedService,
    private val streamService: StreamService,
    private val trendingService: TrendingService,
    private val discoveryAssembler: HomeRecommendationDiscoveryAssembler = HomeRecommendationDiscoveryAssembler(),
    private val shortsCandidateService: HomeRecommendationShortsCandidateService = HomeRecommendationShortsCandidateService(),
) {
    private val relatedCandidateService = HomeRecommendationRelatedCandidateService(streamService)

    suspend fun fetchCandidates(
        userId: String,
        serviceId: Int,
        profile: HomeRecommendationProfile,
        mode: HomeRecommendationPoolMode,
        signalContext: HomeRecommendationSignalContext = HomeRecommendationSignalContext(),
    ): HomeRecommendationCandidatePool {
        if (mode == HomeRecommendationPoolMode.FAST_SHORTS) {
            val subscriptions = subscriptionShortsFeedService.getCachedFeed(
                userId = userId,
                page = 0,
                limit = HomeRecommendationCandidateLimits.FAST_SUBSCRIPTION_PAGE_SIZE,
            )
                ?.videos
                .orEmpty()
                .map { HomeRecommendationTaggedVideo(it, HomeRecommendationSourceTag.SUBSCRIPTION) }
            return HomeRecommendationCandidatePool(subscriptions = subscriptions, discovery = emptyList())
        }
        if (mode == HomeRecommendationPoolMode.SHORTS) {
            if (serviceId != YOUTUBE_SERVICE_ID) {
                return HomeRecommendationCandidatePool(subscriptions = emptyList(), discovery = emptyList())
            }
            return shortsCandidateService.fetch(userId, profile, signalContext, this)
        }
        val subscriptions = fetchSubscriptionCandidates(userId, mode)
            .filter { recommendationServiceId(it.url) == serviceId }
            .map { HomeRecommendationTaggedVideo(it, HomeRecommendationSourceTag.SUBSCRIPTION) }
        if (mode == HomeRecommendationPoolMode.FAST) {
            val discovery = if (subscriptions.isEmpty()) fetchTrending(serviceId) else emptyList()
            return HomeRecommendationCandidatePool(subscriptions = subscriptions, discovery = discovery)
        }
        val subscriptionSeeds = subscriptions.map { it.video.url }
        val relatedFromSubscriptions = relatedCandidateService.fetch(
            seedUrls = subscriptionSeeds,
            source = HomeRecommendationSourceTag.DISCOVERY_THEME,
            seedLimit = HomeRecommendationCandidateLimits.SUBSCRIPTION_SEED_LIMIT,
            relatedPerSeedLimit = HomeRecommendationCandidateLimits.RELATED_PER_SEED_LIMIT,
        )
        val relatedFromFavorites = relatedCandidateService.fetch(
            seedUrls = signalContext.favoriteUrls.filter { recommendationServiceId(it) == serviceId },
            source = HomeRecommendationSourceTag.DISCOVERY_EXPLORATION,
            seedLimit = HomeRecommendationCandidateLimits.FAVORITE_SEED_LIMIT,
            relatedPerSeedLimit = HomeRecommendationCandidateLimits.RELATED_PER_SEED_LIMIT,
        )
        val discovery = discoveryAssembler.build(
            profile = profile,
            candidates = relatedFromSubscriptions + relatedFromFavorites + fetchTrending(serviceId),
            explorationCap = HomeRecommendationCandidateLimits.RELATED_DISCOVERY_CAP,
        ).filter { recommendationServiceId(it.video.url) == serviceId }
        return HomeRecommendationCandidatePool(subscriptions = subscriptions, discovery = discovery)
    }

    suspend fun fetchSubscriptionCandidates(userId: String, mode: HomeRecommendationPoolMode): List<VideoItem> {
        if (mode == HomeRecommendationPoolMode.SHORTS) {
            return HomeRecommendationShortSubscriptionSource.fetch(userId, subscriptionShortsFeedService, subscriptionFeedService)
        }
        if (mode == HomeRecommendationPoolMode.FAST) {
            return subscriptionFeedService.getCachedFeed(
                userId = userId,
                page = 0,
                limit = HomeRecommendationCandidateLimits.FAST_SUBSCRIPTION_PAGE_SIZE,
            )
                ?.videos
                .orEmpty()
        }
        val pages = listOf(
            subscriptionFeedService.getFeed(userId = userId, page = 0, limit = 120).videos,
            subscriptionFeedService.getFeed(userId = userId, page = 1, limit = 120).videos,
        )
        return pages.flatten()
    }

    suspend fun fetchRelatedCandidates(
        seedUrls: List<String>,
        source: HomeRecommendationSourceTag,
        seedLimit: Int,
        relatedPerSeedLimit: Int,
    ): List<HomeRecommendationTaggedVideo> =
        relatedCandidateService.fetch(seedUrls, source, seedLimit, relatedPerSeedLimit)

    private suspend fun fetchTrending(serviceId: Int): List<HomeRecommendationTaggedVideo> =
        when (val result = trendingService.getTrending(serviceId)) {
            is ExtractionResult.Success -> result.data
                .filter { recommendationServiceId(it.url) == serviceId }
                .map { HomeRecommendationTaggedVideo(it, HomeRecommendationSourceTag.DISCOVERY_TRENDING) }
            is ExtractionResult.BadRequest -> emptyList()
            is ExtractionResult.Failure -> emptyList()
        }
}
