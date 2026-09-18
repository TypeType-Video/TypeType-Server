package dev.typetype.server.models

data class YoutubeTakeoutParsedData(
    val subscriptions: List<SubscriptionItem>,
    val playlists: List<PlaylistItem>,
    val playlistItems: Map<String, List<PlaylistVideoItem>>,
    val favorites: List<FavoriteItem>,
    val watchLater: List<PlaylistVideoItem>,
    val history: List<HistoryItem>,
    val warnings: List<String>,
    val errors: List<String>,
)
