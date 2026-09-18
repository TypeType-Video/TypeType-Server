package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ActiveSessionNowPlayingItem(
    val videoUrl: String,
    val title: String,
    val thumbnail: String? = null,
    val channelName: String? = null,
    val positionMs: Long,
    val durationMs: Long? = null,
    val paused: Boolean,
    val updatedAt: Long,
)
