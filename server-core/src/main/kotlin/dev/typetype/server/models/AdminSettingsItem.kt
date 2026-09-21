package dev.typetype.server.models

import dev.typetype.server.DEFAULT_INSTANCE_NAME
import kotlinx.serialization.Serializable

@Serializable
data class AdminSettingsItem(
    val name: String = DEFAULT_INSTANCE_NAME,
    val tagline: String? = null,
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val minAndroidClientVersion: String? = null,
    val allowRegistration: Boolean = true,
    val allowGuest: Boolean = true,
    val forceEmailVerification: Boolean = false,
    val activeSessionsEnabled: Boolean = false,
    val localLoginEnabled: Boolean = true,
    val oidcAutoRedirect: Boolean = false,
    val youtubeRemoteLoginEnabled: Boolean = false,
    val accessMode: String = "unrestricted",
    val rssEnabled: Boolean = false,
    val rssPublicBaseUrl: String? = null,
    val rssMaxFeedsPerUser: Int = 10,
    val rssMaxItems: Int = 50,
    val rssMinimumPollMinutes: Int = 5,
    val rssRateLimitPerMinute: Int = 30,
)
