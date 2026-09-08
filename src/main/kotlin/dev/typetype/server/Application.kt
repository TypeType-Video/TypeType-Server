package dev.typetype.server
import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.downloader.YoutubeProxySelector
import dev.typetype.server.services.ActiveSessionService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.AuthSessionConfig
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.DownloaderGatewayService
import dev.typetype.server.services.GitHubIssueService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import dev.typetype.server.services.ProfileAccountService
import dev.typetype.server.services.PipePipeBackupImporterService
import dev.typetype.server.services.OpenMojiProxyService
import dev.typetype.server.services.InstanceService
import dev.typetype.server.services.InternalHealthService
import dev.typetype.server.services.NewPipeInitializer
import dev.typetype.server.services.OidcAuthService
import dev.typetype.server.services.OidcConfigLoader
import dev.typetype.server.services.OkHttpYoutubeRemoteBrowserClient
import dev.typetype.server.services.SecretConfigReader
import dev.typetype.server.services.UserAdminService
import dev.typetype.server.services.YoutubeRemoteBrowserConfig
import dev.typetype.server.services.YoutubeRemoteBrowserService
import dev.typetype.server.services.YoutubeRemoteLoginReadinessService
import dev.typetype.server.portability.PortabilityEngineFactory
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Files
import java.util.UUID

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    launchExtractorLifecycle()
    val dbUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/typetype"
    val dbUser = System.getenv("DATABASE_USER") ?: "typetype"
    val dbPassword = System.getenv("DATABASE_PASSWORD") ?: "typetype"
    DatabaseFactory.init(dbUrl, dbUser, dbPassword)
    val jwtSecret = System.getenv("JWT_SECRET") ?: UUID.randomUUID().toString()
    val authSessionConfig = AuthSessionConfig.fromEnvironment()
    val profileAccountService = ProfileAccountService()
    val authService = AuthService(jwtSecret, sessionConfig = authSessionConfig, profileAccountService = profileAccountService)
    val oidcAuthService = OidcAuthService(OidcConfigLoader.fromEnvironment(), jwtSecret, authService)
    val userAdminService = UserAdminService()
    val passwordResetService = PasswordResetService()
    val profileService = ProfileService()
    val avatarService = AvatarService()
    val gitHubIssueService = GitHubIssueService()
    val adminSettingsService = AdminSettingsService()
    val activeSessionService = ActiveSessionService(adminSettingsService)
    val restoreService = PipePipeBackupImporterService()
    val downloaderServiceUrl = System.getenv("DOWNLOADER_SERVICE_URL") ?: "http://typetype-downloader:18093"
    val youtubeSessionEncryptionKey = SecretConfigReader.read("YOUTUBE_SESSION_ENCRYPTION_KEY")
    val cacheUrl = System.getenv("DRAGONFLY_URL") ?: "redis://localhost:6379"
    val cache = DragonflyService(cacheUrl)
    val subtitleServiceUrl = System.getenv("SUBTITLE_SERVICE_URL") ?: "http://typetype-token:8081"
    val youtubeProxySelector = YoutubeProxySelector.fromUrl(System.getenv("YOUTUBE_OUTBOUND_PROXY_URL"))
    NewPipeInitializer.init(subtitleServiceUrl, youtubeProxySelector)
    val svc = ServiceRegistry(
        cache,
        subtitleServiceUrl,
        youtubeSessionEncryptionKey,
        jwtSecret,
        adminSettingsService,
        youtubeProxySelector,
        profileAccountService,
    )
    val youtubeRemoteBrowserConfig = YoutubeRemoteBrowserConfig.fromEnvironment(subtitleServiceUrl)
    val youtubeRemoteLoginReadinessService = YoutubeRemoteLoginReadinessService(
        youtubeRemoteBrowserConfig,
        svc.youtubeSessionService,
    )
    val instanceService = InstanceService(
        authService,
        adminSettingsService,
        youtubeRemoteLoginStatusProvider = youtubeRemoteLoginReadinessService::status,
        oidcConfigProvider = oidcAuthService::publicConfig,
    )
    val youtubeRemoteBrowserService = YoutubeRemoteBrowserService(
        youtubeRemoteBrowserConfig,
        adminSettingsService,
        svc.youtubeSessionService,
        OkHttpYoutubeRemoteBrowserClient(youtubeRemoteBrowserConfig.serviceUrl),
    )
    val downloaderGatewayService = DownloaderGatewayService(downloaderServiceUrl)
    val openMojiProxyService = OpenMojiProxyService(cache)
    val internalHealthService = InternalHealthService(cache, downloaderGatewayService, subtitleServiceUrl)
    val portabilityEngine = PortabilityEngineFactory.create(
        Files.createTempDirectory("typetype-portability-"),
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
    monitor.subscribe(ApplicationStopped) { portabilityEngine.close() }
    configurePlugins(authService)
    installApplicationRoutes(
        svc = svc,
        authService = authService,
        authSessionConfig = authSessionConfig,
        adminSettingsService = adminSettingsService,
        activeSessionService = activeSessionService,
        downloaderGatewayService = downloaderGatewayService,
        gitHubIssueService = gitHubIssueService,
        instanceService = instanceService,
        oidcAuthService = oidcAuthService,
        passwordResetService = passwordResetService,
        profileService = profileService,
        profileAccountService = profileAccountService,
        userAdminService = userAdminService,
        avatarService = avatarService,
        openMojiProxyService = openMojiProxyService,
        internalHealthService = internalHealthService,
        restoreService = restoreService,
        youtubeRemoteBrowserService = youtubeRemoteBrowserService,
        portabilityEngine = portabilityEngine,
    )
}
