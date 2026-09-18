package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PublicPlaylistResponse(
    val playlist: PublicPlaylistItem,
    val videos: List<VideoItem>,
    val nextpage: String?,
)
