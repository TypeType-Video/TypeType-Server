package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchPageResponse(
    val items: List<VideoItem>,
    val nextpage: String?,
    val searchSuggestion: String?,
    val isCorrectedSearch: Boolean,
    val playlists: List<PlaylistResultItem> = emptyList(),
    val channels: List<ChannelResultItem> = emptyList(),
)
