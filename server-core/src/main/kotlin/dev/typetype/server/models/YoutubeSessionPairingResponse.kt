package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeSessionPairingResponse(
    val code: String,
    val expiresAt: Long,
)
