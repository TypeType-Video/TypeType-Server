package dev.typetype.server

import dev.typetype.server.routes.adminBugReportRoutes
import dev.typetype.server.routes.adminAllowListRoutes
import dev.typetype.server.routes.adminRoutes
import dev.typetype.server.routes.adminRssRoutes
import dev.typetype.server.routes.adminIdentityRoutes
import dev.typetype.server.routes.adminSessionRoutes
import dev.typetype.server.routes.authRoutes
import dev.typetype.server.routes.accountProfilesRoutes
import dev.typetype.server.routes.avatarRoutes
import dev.typetype.server.routes.bulletCommentRoutes
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.routes.commentRoutes
import dev.typetype.server.routes.downloaderGatewayRoutes
import dev.typetype.server.routes.deArrowRoutes
import dev.typetype.server.routes.internalObservabilityRoutes
import dev.typetype.server.routes.oidcAuthRoutes
import dev.typetype.server.routes.podcastRoutes
import dev.typetype.server.routes.publicMetadataRoutes
import dev.typetype.server.routes.publicPlaylistRoutes
import dev.typetype.server.routes.pushNotificationRoutes
import dev.typetype.server.routes.rssPublicRoutes
import dev.typetype.server.routes.sabrRoutes
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.routes.sessionActivityRoutes
import dev.typetype.server.routes.suggestionRoutes
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.routes.userDataRoutes
import dev.typetype.server.routes.youtubeRemoteBrowserRoutes
import dev.typetype.server.services.ActiveSessionService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AuthSessionConfig
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.DownloaderGatewayService
import dev.typetype.server.services.GitHubIssueService
import dev.typetype.server.services.InstanceService
import dev.typetype.server.services.InternalHealthService
import dev.typetype.server.services.OidcAuthService
import dev.typetype.server.services.OpenMojiProxyService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.PipePipeBackupImporterService
import dev.typetype.server.services.ProfileService
import dev.typetype.server.services.ProfileAccountService
import dev.typetype.server.services.UserAdminService
import dev.typetype.server.services.YoutubeRemoteBrowserService
import dev.typetype.server.portability.PortabilityEngine
import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing

internal fun Application.installApplicationRoutes(
    svc: ServiceRegistry,
    authService: AuthService,
    authSessionConfig: AuthSessionConfig,
    adminSettingsService: AdminSettingsService,
    activeSessionService: ActiveSessionService,
    downloaderGatewayService: DownloaderGatewayService,
    gitHubIssueService: GitHubIssueService,
    instanceService: InstanceService,
    oidcAuthService: OidcAuthService,
    passwordResetService: PasswordResetService,
    profileService: ProfileService,
    profileAccountService: ProfileAccountService,
    userAdminService: UserAdminService,
    avatarService: AvatarService,
    openMojiProxyService: OpenMojiProxyService,
    internalHealthService: InternalHealthService,
    restoreService: PipePipeBackupImporterService,
    youtubeRemoteBrowserService: YoutubeRemoteBrowserService,
    portabilityEngine: PortabilityEngine,
) {
    routing {
        internalObservabilityRoutes(internalHealthService::check)
        publicMetadataRoutes(instanceService::getInstance)
        rssPublicRoutes(svc.rssFeedReaderService)
        installStreamRoutes(svc, authService, adminSettingsService)
        rateLimit(DEARROW_ZONE) { deArrowRoutes(svc.deArrowService) }
        rateLimit(EXTRACTION_ZONE) {
            searchRoutes(svc.searchService, authService, svc.accessControlService, adminSettingsService, svc.blockedService)
            suggestionRoutes(svc.suggestionService, authService, adminSettingsService)
            trendingRoutes(svc.trendingService, authService, svc.accessControlService, adminSettingsService)
            publicPlaylistRoutes(svc.publicPlaylistService, authService, svc.accessControlService, adminSettingsService)
            commentRoutes(svc.commentService, authService, adminSettingsService)
            bulletCommentRoutes(svc.bulletCommentService, authService, adminSettingsService)
        }
        rateLimit(CHANNEL_ZONE) {
            channelRoutes(svc.channelService, authService, svc.accessControlService, adminSettingsService)
            podcastRoutes(svc.podcastService, authService, adminSettingsService)
        }
        installProxyRoutes(svc)
        rateLimit(PROXY_ZONE) {
            sabrRoutes(
                svc.sabrSessionStore,
                svc.youtubeSabrStreamService,
                authService,
                svc.accessControlService,
                adminSettingsService,
                svc.audioOnlyMediaTokenService,
                svc.authenticatedSabrInfoService,
                svc.youtubeSessionSabrStreamService?.let { service ->
                    { userId, url -> service.getStreamInfo(userId, url) }
                },
            )
        }
        downloaderGatewayRoutes(downloaderGatewayService)
        oidcAuthRoutes(oidcAuthService, adminSettingsService, authSessionConfig)
        authRoutes(
            authService,
            passwordResetService,
            profileService,
            adminSettingsService,
            svc.homeRecommendationWarmupService,
            authSessionConfig,
        )
        accountProfilesRoutes(profileAccountService, authService, authSessionConfig)
        adminRoutes(authService, userAdminService, passwordResetService, adminSettingsService)
        adminRssRoutes(svc.rssFeedManagementService, authService)
        adminIdentityRoutes(svc.accountIdentityService, authService)
        adminAllowListRoutes(authService, userAdminService, svc.adminManagedAccessService, svc.adminUserLookupService, svc.allowedChannelsService, svc.allowedPlaylistsService)
        adminSessionRoutes(authService, activeSessionService)
        sessionActivityRoutes(authService, activeSessionService)
        adminBugReportRoutes(authService, svc.bugReportService, gitHubIssueService)
        avatarRoutes(avatarService, openMojiProxyService, svc.customAvatarService)
        rateLimit(USER_DATA_ZONE) { youtubeRemoteBrowserRoutes(youtubeRemoteBrowserService, authService) }
        rateLimit(USER_DATA_ZONE) {
            userDataRoutes(
                svc,
                authService,
                profileService,
                avatarService,
                svc.bugReportService,
                restoreService,
                portabilityEngine,
            )
        }
    }
}
