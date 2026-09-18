package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteItem(
    val videoUrl: String,
    val favoritedAt: Long = 0L,
    val title: String = "",
    val thumbnail: String = "",
    val duration: Long = 0L,
    val channelName: String = "",
    val channelUrl: String = "",
    val channelAvatar: String = "",
    val viewCount: Long = 0L,
    val publishedAt: Long = -1L,
)
