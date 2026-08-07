package dev.typetype.server
import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AccountIdentityService
import dev.typetype.server.services.AdminManagedAccessService
import dev.typetype.server.services.AdminUserLookupService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.CustomAvatarService
import dev.typetype.server.services.DeArrowService
import dev.typetype.server.services.BugReportService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.NotificationsService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.SavedPlaylistService
import dev.typetype.server.services.SearchHistoryService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.HomeRecommendationPoolResolverDependencies
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionShortsBlendService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionFeedCacheInvalidation
import dev.typetype.server.services.SubscriptionFeedCacheInvalidator
import dev.typetype.server.services.TypeTypeBackupService
import dev.typetype.server.services.UserVideoMetadataRepairService
import dev.typetype.server.services.VideoMetadataResolver
import dev.typetype.server.services.WatchLaterService
import dev.typetype.server.services.YoutubeTakeoutFactory
import java.net.ProxySelector
internal class ServiceRegistry(
    cache: DragonflyService,
    subtitleServiceUrl: String,
    youtubeSessionEncryptionKey: String?,
    jwtSecret: String,
    adminSettingsService: AdminSettingsService,
    youtubeProxySelector: ProxySelector? = null,
) {
    val publicHlsManifestTokenService = PublicHlsManifestTokenService(jwtSecret)
    val accountIdentityService = AccountIdentityService()
    val customAvatarService = CustomAvatarService()
    val deArrowService = DeArrowService(cache)
    private val extraction = ExtractionServiceRegistry(
        cache,
        subtitleServiceUrl,
        youtubeSessionEncryptionKey,
        publicHlsManifestTokenService::createPath,
        youtubeProxySelector,
    )
    val youtubeSessionService = extraction.youtubeSessionService
    val youtubeSessionStreamService = extraction.youtubeSessionStreamService
    val youtubeSabrStreamService = extraction.youtubeSabrStreamService
    val youtubeSabrBootstrapStreamService = extraction.youtubeSabrBootstrapStreamService
    val nicoNicoStreamService = extraction.nicoNicoStreamService
    val bilibiliStreamService = extraction.bilibiliStreamService
    val streamService = extraction.streamService
    val searchService = extraction.searchService
    val trendingService = extraction.trendingService
    val commentService = extraction.commentService
    val bulletCommentService = extraction.bulletCommentService
    val channelService = extraction.channelService
    val podcastService = extraction.podcastService
    val publicPlaylistService = extraction.publicPlaylistService
    val proxyService = extraction.proxyService
    val youtubeSubtitleDeliveryService = extraction.youtubeSubtitleDeliveryService
    val nicoVideoProxyService = extraction.nicoVideoProxyService
    val manifestService = extraction.manifestService
    val nativeManifestService = extraction.nativeManifestService
    val hlsManifestService = extraction.hlsManifestService
    val youtubeSessionHlsManifestService = extraction.youtubeSessionHlsManifestService
    val audioOnlyMediaTokenService = AudioOnlyMediaTokenService(jwtSecret)
    val suggestionService = extraction.suggestionService
    val sabrSessionStore = extraction.sabrSessionStore
    val historyService = HistoryService()
    val subscriptionsService = SubscriptionsService()
    val subscriptionGroupsService = SubscriptionGroupsService()
    val subscriptionFeedService = SubscriptionFeedService(subscriptionsService, channelService, cache)
    val subscriptionShortsFeedService = SubscriptionShortsFeedService(
        subscriptionsService,
        channelService,
        SubscriptionShortsBlendService(trendingService),
        cache,
    )
    init {
        SubscriptionFeedCacheInvalidation.configure(
            SubscriptionFeedCacheInvalidator(cache, subscriptionFeedService),
        )
    }
    val notificationsService = NotificationsService(subscriptionFeedService)
    val playlistService = PlaylistService()
    val videoMetadataRepairService = UserVideoMetadataRepairService(VideoMetadataResolver(streamService))
    val savedPlaylistService = SavedPlaylistService()
    val watchLaterService = WatchLaterService()
    val progressService = ProgressService()
    val favoritesService = FavoritesService()
    val settingsService = SettingsService()
    val searchHistoryService = SearchHistoryService()
    val allowedChannelsService = AllowedChannelsService()
    val allowedPlaylistsService = AllowedPlaylistsService()
    val adminManagedAccessService = AdminManagedAccessService()
    val adminUserLookupService = AdminUserLookupService()
    val accessControlService = AccessControlService(settingsService, allowedChannelsService, allowedPlaylistsService, adminSettingsService)
    val blockedService = BlockedService()
    val typeTypeBackupService = TypeTypeBackupService(
        subscriptionsService,
        historyService,
        playlistService,
        watchLaterService,
        favoritesService,
        progressService,
        searchHistoryService,
        savedPlaylistService,
        settingsService,
        blockedService,
        allowedChannelsService,
        allowedPlaylistsService,
    )
    val bugReportService = BugReportService()
    val youtubeTakeoutImportService = YoutubeTakeoutFactory.create(subscriptionsService, playlistService, historyService, favoritesService, watchLaterService)
    val recommendationPoolResolverDependencies = HomeRecommendationPoolResolverDependencies(
        subscriptionsService = subscriptionsService,
        subscriptionFeedService = subscriptionFeedService,
        subscriptionShortsFeedService = subscriptionShortsFeedService,
        historyService = historyService,
        favoritesService = favoritesService,
        watchLaterService = watchLaterService,
        blockedService = blockedService,
        streamService = streamService,
        cache = cache,
    )
    private val homeRecommendationServices = createHomeRecommendationServices(cache, recommendationPoolResolverDependencies)
    val homeRecommendationService = homeRecommendationServices.recommendationService
    val homeRecommendationWarmupService = homeRecommendationServices.warmupService
}
