package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PodcastEpisodesResponse(
    val podcast: PodcastItem,
    val episodes: List<VideoItem>,
    val nextpage: String?,
)
