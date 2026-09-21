package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HomeRecommendationContext
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.HomeRecommendationPoolResolverDependencies
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionShortsBlendService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import dev.typetype.server.services.WatchLaterService

fun homeResolverDependencies(
    subscriptions: SubscriptionsService,
    channelService: ChannelService,
    cache: CacheService,
    trendingService: TrendingService,
): HomeRecommendationPoolResolverDependencies = HomeRecommendationPoolResolverDependencies(
    subscriptionsService = subscriptions,
    subscriptionFeedService = SubscriptionFeedService(subscriptions, channelService, cache),
    subscriptionShortsFeedService = SubscriptionShortsFeedService(
        subscriptions,
        channelService,
        SubscriptionShortsBlendService(trendingService),
        cache,
    ),
    historyService = HistoryService(),
    favoritesService = FavoritesService(),
    watchLaterService = WatchLaterService(),
    blockedService = BlockedService(),
    trendingService = trendingService,
    cache = cache,
)

fun buildHomeResolver(dependencies: HomeRecommendationPoolResolverDependencies): HomeRecommendationPoolResolver =
    HomeRecommendationPoolResolver(dependencies)

fun defaultContext(serviceId: Int = 0): HomeRecommendationContext = HomeRecommendationContext(
    serviceId = serviceId,
    sessionContext = HomeRecommendationSessionContext(
        intent = HomeRecommendationSessionIntent.AUTO,
        deviceClass = HomeRecommendationDeviceClass.UNKNOWN,
    ),
)
