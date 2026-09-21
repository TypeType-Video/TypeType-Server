package dev.typetype.server.services

data class VideoMetadataItem(
    val url: String,
    val title: String,
    val thumbnail: String,
    val duration: Long,
    val channelName: String,
    val channelUrl: String,
    val channelAvatar: String,
    val viewCount: Long,
    val publishedAt: Long,
)
