package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeTakeoutCommitRequest(
    val importSubscriptions: Boolean = true,
    val importPlaylists: Boolean = true,
    val importPlaylistItems: Boolean = true,
    val importFavorites: Boolean = true,
    val importWatchLater: Boolean = true,
    val importHistory: Boolean = true,
)
