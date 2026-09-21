package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AllowedPlaylistItem(
    val url: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val uploaderName: String? = null,
    val allowedAt: Long = 0L,
    val global: Boolean? = null,
)
