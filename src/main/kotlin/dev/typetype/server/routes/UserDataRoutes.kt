package dev.typetype.server.routes

import dev.typetype.server.ServiceRegistry
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.BugReportService
import dev.typetype.server.services.PipePipeBackupImporterService
import dev.typetype.server.services.ProfileService
import dev.typetype.server.portability.PortabilityEngine
import io.ktor.server.routing.Route

internal fun Route.userDataRoutes(
    svc: ServiceRegistry,
    authService: AuthService,
    profileService: ProfileService,
    avatarService: AvatarService,
    bugReportService: BugReportService,
    restoreService: PipePipeBackupImporterService,
    portabilityEngine: PortabilityEngine,
) {
    historyRoutes(svc.historyService, authService, svc.settingsService)
    subscriptionGroupsRoutes(svc.subscriptionGroupsService, authService)
    subscriptionsRoutes(
        svc.subscriptionsService,
        authService,
        svc.homeRecommendationWarmupService,
        svc.subscriptionGroupsService,
        svc.pushNotificationService,
    )
    subscriptionFeedRoutes(
        svc.subscriptionFeedService,
        authService,
        svc.settingsService,
        svc.subscriptionGroupsService,
    )
    subscriptionShortsFeedRoutes(svc.subscriptionShortsFeedService, authService)
    rssFeedRoutes(svc.rssFeedManagementService, authService)
    playlistRoutes(svc.playlistService, authService, svc.videoMetadataRepairService)
    savedPlaylistRoutes(svc.savedPlaylistService, svc.publicPlaylistService, authService)
    watchLaterRoutes(svc.watchLaterService, authService, svc.videoMetadataRepairService)
    progressRoutes(svc.progressService, authService, svc.settingsService)
    favoritesRoutes(svc.favoritesService, authService, svc.videoMetadataRepairService)
    settingsRoutes(svc.settingsService, authService)
    searchHistoryRoutes(svc.searchHistoryService, authService)
    allowedChannelsRoutes(svc.allowedChannelsService, authService)
    blockedRoutes(svc.blockedService, authService)
    notificationsRoutes(svc.notificationsService, authService)
    pushNotificationRoutes(svc.pushNotificationService, authService)
    youtubeSessionRoutes(svc.youtubeSessionService, authService)
    youtubeTakeoutImportRoutes(svc.youtubeTakeoutImportService, authService)
    profileRoutes(profileService, avatarService, svc.customAvatarService, authService)
    customAvatarRoutes(svc.customAvatarService, authService)
    accountIdentityRoutes(svc.accountIdentityService, authService)
    bugReportRoutes(bugReportService, authService)
    restoreRoutes(restoreService, authService)
    typeTypeBackupRoutes(svc.typeTypeBackupService, authService)
    portabilityRoutes(portabilityEngine, authService)
    homeRecommendationRoutes(svc.homeRecommendationService, authService, svc.blockedService, svc.accessControlService)
    homeRecommendationShortsRoutes(svc.homeRecommendationService, authService, svc.blockedService, svc.accessControlService)
}
