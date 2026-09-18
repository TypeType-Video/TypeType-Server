package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PublicPlaylistItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val streamCount: Long,
    val playlistType: String,
)
