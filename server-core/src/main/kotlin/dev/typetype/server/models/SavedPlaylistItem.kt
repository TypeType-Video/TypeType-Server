package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SavedPlaylistItem(
    val id: String,
    val publicPlaylistId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val streamCount: Long,
    val playlistType: String,
    val savedAt: Long,
)
