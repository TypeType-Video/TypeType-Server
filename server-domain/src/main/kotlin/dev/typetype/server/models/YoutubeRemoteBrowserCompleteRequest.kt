package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeRemoteBrowserCompleteRequest(
    val sessionId: String,
    val tokenSessionId: String,
    val status: String,
    val cookies: String,
    val poToken: String,
    val authUser: Int = 0,
    val capturedAt: Long,
)
