package dev.typetype.server.models

import dev.typetype.server.currentRequestId
import kotlinx.serialization.Serializable

@Serializable
data class FocusStudyChannelRequest(
    val channelUrl: String,
    val name: String = "",
    val avatarUrl: String = "",
)

@Serializable
data class FocusStudyChannelResponse(
    val channelUrl: String,
    val status: String,
    val unlockAt: Long? = null,
)

@Serializable
data class FocusBlockedErrorResponse(
    val error: String,
    val code: String,
    val channelUrl: String = "",
    val channelName: String = "",
    val channelAvatarUrl: String = "",
    val videoTitle: String = "",
    val unlockAt: Long? = null,
    val focusAiEnabled: Boolean = false,
    val requestId: String? = currentRequestId(),
)
