package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class StreamSegmentItem(
    val title: String,
    val startTimeSeconds: Int,
    val channelName: String?,
    val url: String?,
    val previewUrl: String?,
)
