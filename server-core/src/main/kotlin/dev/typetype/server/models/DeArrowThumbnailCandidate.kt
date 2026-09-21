package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class DeArrowThumbnailCandidate(
    val timestamp: Double? = null,
    val thumbnailUrl: String? = null,
    val original: Boolean,
    val votes: Int,
    val locked: Boolean,
    val uuid: String,
)
