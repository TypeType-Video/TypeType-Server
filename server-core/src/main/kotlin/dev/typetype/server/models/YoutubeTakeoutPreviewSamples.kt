package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeTakeoutPreviewSamples(
    val subscriptions: List<SubscriptionItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val playlistItems: List<PlaylistVideoItem> = emptyList(),
    val favorites: List<String> = emptyList(),
    val watchLater: List<PlaylistVideoItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
)
