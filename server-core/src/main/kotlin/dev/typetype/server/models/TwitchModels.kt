package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class TwitchCapability(
    val enabled: Boolean = false,
)

@Serializable
data class TwitchDevicePollRequest(
    val deviceCode: String = "",
)

@Serializable
data class TwitchChatMessageRequest(
    val message: String = "",
)

@Serializable
data class TwitchAccountStatus(
    val linked: Boolean,
    val login: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class TwitchPollResponse(
    val status: String,
    val account: TwitchAccountStatus? = null,
)
