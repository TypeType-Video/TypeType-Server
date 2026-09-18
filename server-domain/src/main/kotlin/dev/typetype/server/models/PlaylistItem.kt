package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistItem(
    val id: String = "",
    val name: String,
    val description: String = "",
    val videos: List<PlaylistVideoItem> = emptyList(),
    val videoCount: Int = videos.size,
    val createdAt: Long = 0L,
)
