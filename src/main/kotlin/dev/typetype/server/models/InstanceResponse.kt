package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class InstanceResponse(
    val name: String,
    val tagline: String? = null,
    val version: String,
    val revision: String,
    val shortRevision: String,
    val buildTime: String,
    val apiVersion: Int,
    val registrationAllowed: Boolean,
    val guestAllowed: Boolean,
    val supportedServices: List<Int>,
    val minClientVersion: InstanceMinClientVersion = InstanceMinClientVersion(),
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val localLoginEnabled: Boolean = true,
    val oidcEnabled: Boolean = false,
    val oidcProviderName: String? = null,
    val oidcAutoRedirect: Boolean = false,
    val youtubeRemoteLoginEnabled: Boolean = false,
    val youtubeRemoteLoginReady: Boolean = false,
    val youtubeRemoteLoginUnavailableReason: String? = null,
    val parentalControlsEnabled: Boolean = false,
    val rss: RssInstanceCapability = RssInstanceCapability(),
    val pushNotifications: PushNotificationCapability = PushNotificationCapability(),
)

@Serializable
data class RssInstanceCapability(
    val enabled: Boolean = false,
    val maxFeedsPerUser: Int = 0,
    val maxItems: Int = 0,
    val minimumPollMinutes: Int = 0,
    val rateLimitPerMinute: Int = 0,
)
