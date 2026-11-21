package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SeekbarIconItem(
    val seekbarIconUrl: String,
    val mediaType: String,
    val size: Int,
)
