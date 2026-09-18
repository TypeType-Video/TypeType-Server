package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeRemoteBrowserTokenStartRequest(
    val serverSessionId: String,
    val userId: String,
    val callbackUrl: String,
    val ttlMs: Long,
)
