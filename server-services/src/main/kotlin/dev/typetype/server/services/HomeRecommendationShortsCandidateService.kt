package dev.typetype.server.services

class HomeRecommendationShortsCandidateService {
    suspend fun fetch(
        userId: String,
        profile: HomeRecommendationProfile,
        signalContext: HomeRecommendationSignalContext,
        candidateService: HomeRecommendationCandidateService,
    ): HomeRecommendationCandidatePool {
        val subscriptions = candidateService.fetchSubscriptionCandidates(userId, HomeRecommendationPoolMode.SHORTS)
            .asSequence()
            .filter(HomeRecommendationShortsClassifier::isShort)
            .map { it.copy(isShortFormContent = true) }
            .take(HomeRecommendationShortsSources.SUBSCRIPTION_LIMIT)
            .map { HomeRecommendationTaggedVideo(it, HomeRecommendationSourceTag.SUBSCRIPTION) }
            .toList()
        val relatedFromSubscriptions = candidateService.fetchRelatedCandidates(
            seedUrls = subscriptions.map { it.video.url },
            source = HomeRecommendationSourceTag.DISCOVERY_THEME,
            seedLimit = HomeRecommendationCandidateLimits.SUBSCRIPTION_SEED_LIMIT,
            relatedPerSeedLimit = HomeRecommendationCandidateLimits.RELATED_PER_SEED_LIMIT,
        )
            .asSequence()
            .map { it.copy(video = it.video.copy(isShortFormContent = it.video.isShortFormContent || it.video.duration in 1L..85L)) }
            .filter { it.video.isShortFormContent }
            .toList()
        val relatedFromFavorites = candidateService.fetchRelatedCandidates(
            seedUrls = signalContext.favoriteUrls,
            source = HomeRecommendationSourceTag.DISCOVERY_EXPLORATION,
            seedLimit = HomeRecommendationCandidateLimits.FAVORITE_SEED_LIMIT,
            relatedPerSeedLimit = HomeRecommendationCandidateLimits.RELATED_PER_SEED_LIMIT,
        ).asSequence()
            .map { it.copy(video = it.video.copy(isShortFormContent = it.video.isShortFormContent || it.video.duration in 1L..85L)) }
            .filter { it.video.isShortFormContent }
            .toList()
        val discovery = HomeRecommendationDiscoveryAssembler().build(
            profile = profile,
            candidates = relatedFromSubscriptions + relatedFromFavorites,
            explorationCap = HomeRecommendationShortsSources.DISCOVERY_CAP,
        )
        val dedupedSubscriptions = HomeRecommendationShortsDeduplicator.apply(
            candidates = subscriptions,
            historyUrls = signalContext.historyItems,
            subscriptionChannels = signalContext.userSubscriptions,
        )
        val boostedSubscriptions = HomeRecommendationShortsHistorySignals.promote(profile, dedupedSubscriptions)
        val safeSubscriptions = HomeRecommendationShortProfileFallback.inject(boostedSubscriptions, profile)
        val dedupedDiscovery = HomeRecommendationShortsDeduplicator.apply(
            candidates = discovery,
            historyUrls = signalContext.historyItems,
            subscriptionChannels = signalContext.userSubscriptions,
        )
        val enrichedDiscovery = dedupedDiscovery.take(HomeRecommendationShortsSources.DISCOVERY_REBALANCE_LIMIT)
        return HomeRecommendationShortsFallback.apply(HomeRecommendationCandidatePool(
            subscriptions = safeSubscriptions,
            discovery = enrichedDiscovery,
        ))
    }
}
