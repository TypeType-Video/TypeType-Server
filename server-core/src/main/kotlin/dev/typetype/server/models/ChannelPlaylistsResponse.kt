package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ChannelPlaylistsResponse(
    val playlists: List<PlaylistResultItem>,
    val nextpage: String?,
)
