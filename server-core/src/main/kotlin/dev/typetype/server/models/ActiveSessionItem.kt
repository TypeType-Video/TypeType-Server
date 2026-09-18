package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ActiveSessionItem(
    val id: String,
    val userId: String?,
    val username: String?,
    val clientName: String? = null,
    val clientVersion: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val deviceType: String? = null,
    val userAgent: String? = null,
    val remoteAddress: String? = null,
    val lastActivityAt: Long,
    val lastPlaybackAt: Long? = null,
    val nowPlaying: ActiveSessionNowPlayingItem? = null,
)
