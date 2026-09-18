package dev.typetype.server.services

import dev.typetype.server.models.ActiveSessionNowPlayingItem

data class ActiveSessionRecord(
    val id: String,
    val userId: String,
    val username: String?,
    val clientName: String?,
    val clientVersion: String?,
    val deviceId: String?,
    val deviceName: String?,
    val deviceType: String?,
    val userAgent: String?,
    val lastActivityAt: Long,
    val lastPlaybackAt: Long?,
    val nowPlaying: ActiveSessionNowPlayingItem?,
)
