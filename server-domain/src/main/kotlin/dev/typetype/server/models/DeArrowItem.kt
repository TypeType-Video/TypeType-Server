package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class DeArrowItem(
    val videoId: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val titles: List<DeArrowTitleCandidate> = emptyList(),
    val thumbnails: List<DeArrowThumbnailCandidate> = emptyList(),
    val randomTime: Double? = null,
    val videoDuration: Double? = null,
    val attributionUrl: String = "https://dearrow.ajay.app",
)
