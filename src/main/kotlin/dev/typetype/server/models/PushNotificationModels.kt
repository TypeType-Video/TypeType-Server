package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PushNotificationCapability(
    val enabled: Boolean = false,
    val provider: String = "unifiedpush",
    val eventTypes: List<String> = emptyList(),
    val maxDevicesPerAccount: Int = 0,
)

@Serializable
data class PushDeviceRegistrationRequest(
    val deviceId: String,
    val platform: String = "android",
    val endpoint: String,
    val expiresAt: Long? = null,
)

@Serializable
data class PushDeviceRegistrationResponse(
    val id: String,
    val deviceId: String,
    val platform: String,
    val expiresAt: Long? = null,
    val updatedAt: Long,
)

@Serializable
data class ChannelNotificationPreferenceRequest(
    val channelUrl: String,
    val enabled: Boolean,
)

@Serializable
data class ChannelNotificationPreference(
    val channelUrl: String,
    val enabled: Boolean,
    val updatedAt: Long,
)

@Serializable
data class UnifiedPushNotificationPayload(
    val version: Int = 1,
    val eventType: String,
    val eventId: String,
    val videoId: String,
    val videoUrl: String,
    val channelId: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val instanceId: String,
    val accountId: String,
    val publishedAt: Long,
    val title: String,
)
