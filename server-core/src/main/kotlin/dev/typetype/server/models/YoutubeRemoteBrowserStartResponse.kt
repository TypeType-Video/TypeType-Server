package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeRemoteBrowserStartResponse(
    val sessionId: String,
    val wsUrl: String,
    val expiresAt: Long,
)
