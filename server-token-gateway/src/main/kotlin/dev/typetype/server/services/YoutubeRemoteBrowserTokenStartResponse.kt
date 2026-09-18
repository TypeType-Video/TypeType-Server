package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeRemoteBrowserTokenStartResponse(
    val sessionId: String,
    val expiresAt: Long,
)
