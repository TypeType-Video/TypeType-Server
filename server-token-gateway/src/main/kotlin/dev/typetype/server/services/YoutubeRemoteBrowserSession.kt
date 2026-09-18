package dev.typetype.server.services

data class YoutubeRemoteBrowserSession(
    val sessionId: String,
    val userId: String,
    val wsTokenHash: String,
    val tokenSessionId: String?,
    val expiresAt: Long,
)
