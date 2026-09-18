package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PodcastItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val streamCount: Long,
    val playlistType: String,
)
