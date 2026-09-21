package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionPlaybackStartRequest(
    val clientName: String? = null,
    val clientVersion: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val deviceType: String? = null,
    val videoUrl: String,
    val title: String,
    val thumbnail: String? = null,
    val channelName: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val paused: Boolean = false,
)
