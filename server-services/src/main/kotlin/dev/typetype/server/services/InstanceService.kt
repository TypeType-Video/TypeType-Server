package dev.typetype.server.services

import dev.typetype.server.BuildInfo
import dev.typetype.server.DEFAULT_INSTANCE_NAME
import dev.typetype.server.INSTANCE_API_VERSION
import dev.typetype.server.models.InstanceMinClientVersion
import dev.typetype.server.models.InstanceResponse
import dev.typetype.server.models.OidcPublicConfig
import dev.typetype.server.models.YoutubeRemoteLoginStatus
import dev.typetype.server.models.RssInstanceCapability
import dev.typetype.server.models.PushNotificationCapability

class InstanceService(
    private val authService: AuthService,
    private val adminSettingsService: AdminSettingsService,
    private val oidcConfigProvider: () -> OidcPublicConfig = { OidcPublicConfig(enabled = false) },
    private val youtubeRemoteLoginAvailable: () -> Boolean = { false },
    private val youtubeRemoteLoginStatusProvider: suspend (Boolean) -> YoutubeRemoteLoginStatus = { enabled ->
        when {
            !enabled -> YoutubeRemoteLoginStatus.Disabled
            youtubeRemoteLoginAvailable() -> YoutubeRemoteLoginStatus.Ready
            else -> YoutubeRemoteLoginStatus.NotConfigured
        }
    },
    private val pushNotificationCapabilityProvider: () -> PushNotificationCapability = { PushNotificationCapability() },
) {

    suspend fun getInstance(): InstanceResponse {
        val settings = adminSettingsService.get()
        val oidc = oidcConfigProvider()
        val youtubeRemoteLoginStatus = youtubeRemoteLoginStatusProvider(settings.youtubeRemoteLoginEnabled)
        return InstanceResponse(
            name = settings.name.normalizeName(),
            tagline = settings.tagline.normalizeOptionalText(),
            version = BuildInfo.VERSION,
            revision = BuildInfo.REVISION,
            shortRevision = BuildInfo.SHORT_REVISION,
            buildTime = BuildInfo.BUILD_TIME,
            apiVersion = INSTANCE_API_VERSION,
            registrationAllowed = settings.allowRegistration || !authService.hasAdmin(),
            guestAllowed = settings.allowGuest,
            supportedServices = VALID_SERVICE_IDS.sorted(),
            minClientVersion = InstanceMinClientVersion(android = settings.minAndroidClientVersion.normalizeOptionalText()),
            logoUrl = settings.logoUrl.normalizeOptionalText(),
            bannerUrl = settings.bannerUrl.normalizeOptionalText(),
            localLoginEnabled = settings.localLoginEnabled,
            oidcEnabled = oidc.enabled,
            oidcProviderName = oidc.providerName,
            oidcAutoRedirect = oidc.enabled && settings.oidcAutoRedirect,
            youtubeRemoteLoginEnabled = youtubeRemoteLoginStatus.ready,
            youtubeRemoteLoginReady = youtubeRemoteLoginStatus.ready,
            youtubeRemoteLoginUnavailableReason = youtubeRemoteLoginStatus.unavailableReason,
            parentalControlsEnabled = settings.accessMode == ACCESS_MODE_ALLOW_LIST,
            rss = RssInstanceCapability(
                enabled = settings.rssEnabled && settings.rssPublicBaseUrl != null,
                maxFeedsPerUser = settings.rssMaxFeedsPerUser,
                maxItems = settings.rssMaxItems,
                minimumPollMinutes = settings.rssMinimumPollMinutes,
                rateLimitPerMinute = settings.rssRateLimitPerMinute,
            ),
            pushNotifications = pushNotificationCapabilityProvider(),
        )
    }

    private fun String.normalizeName(): String = trim().takeIf { it.isNotEmpty() } ?: DEFAULT_INSTANCE_NAME

    private fun String?.normalizeOptionalText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
