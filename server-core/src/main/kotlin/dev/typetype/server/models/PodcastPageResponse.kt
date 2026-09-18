package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PodcastPageResponse(
    val channelName: String,
    val channelUrl: String,
    val podcasts: List<PodcastItem>,
    val episodes: List<VideoItem>,
    val nextpage: String?,
)
