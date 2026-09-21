package dev.typetype.server.models

data class YoutubeTakeoutCommitPlan(
    val importSubscriptions: Boolean,
    val importPlaylists: Boolean,
    val importPlaylistItems: Boolean,
    val importFavorites: Boolean,
    val importWatchLater: Boolean,
    val importHistory: Boolean,
)
