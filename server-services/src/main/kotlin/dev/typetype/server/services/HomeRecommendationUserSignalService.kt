package dev.typetype.server.services

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class HomeRecommendationUserSignalService(
    private val subscriptionsService: SubscriptionsService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
) {
    suspend fun load(userId: String): Pair<HomeRecommendationProfile, HomeRecommendationSignalContext> = coroutineScope {
        val subscriptionsDeferred = async { subscriptionsService.getAll(userId) }
        val favoritesDeferred = async { favoritesService.getAll(userId) }
        val watchLaterDeferred = async { watchLaterService.getAll(userId) }
        val historyDeferred = async {
            historyService.search(userId = userId, q = null, from = null, to = null, limit = 240, offset = 0).first
        }
        val blockedVideosDeferred = async { blockedService.getVideos(userId).map { it.url }.toSet() }
        val blockedChannelsDeferred = async { blockedService.getChannels(userId).map { it.url }.toSet() }
        val blockedKeywordsDeferred = async { blockedService.getKeywords(userId).map { it.keyword }.toSet() }
        val subscriptions = subscriptionsDeferred.await()
        val favorites = favoritesDeferred.await()
        val watchLater = watchLaterDeferred.await()
        val historyItems = historyDeferred.await()
        val blockedVideos = blockedVideosDeferred.await()
        val blockedChannels = blockedChannelsDeferred.await()
        val blockedKeywords = blockedKeywordsDeferred.await()
        val seenUrls = historyItems.map { it.url }.toSet()
        val favoriteUrls = favorites.map { it.videoUrl }.toSet()
        val watchLaterUrls = watchLater.map { it.url }.toSet()
        val subscriptionChannels = subscriptions.map { it.channelUrl }.toSet()
        val profile = HomeRecommendationProfile(
            seenUrls = seenUrls,
            blockedVideos = blockedVideos,
            blockedChannels = blockedChannels,
            blockedKeywords = blockedKeywords,
            feedbackBlockedVideos = emptySet(),
            feedbackBlockedChannels = emptySet(),
            subscriptionChannels = subscriptionChannels,
            favoriteUrls = favoriteUrls,
            watchLaterUrls = watchLaterUrls,
            keywordAffinity = emptySet(),
            themeTokens = emptySet(),
            themeQueries = emptyList(),
            personalizationEnabled = false,
        )
        val signalContext = HomeRecommendationSignalContext(
            userSubscriptions = subscriptions.map { it.channelUrl },
            historyItems = historyItems.take(60).map { it.url },
            favoriteUrls = favorites.map { it.videoUrl },
        )
        profile to signalContext
    }
}
