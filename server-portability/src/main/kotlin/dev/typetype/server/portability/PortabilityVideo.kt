package dev.typetype.server.portability

import kotlinx.serialization.Serializable

@Serializable
data class PortabilityVideo(
    val url: String,
    val title: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0L,
    val channelName: String = "",
    val channelUrl: String = "",
    val channelAvatarUrl: String = "",
    val viewCount: Long = 0L,
    val publishedAt: Long = -1L,
)

fun PortabilityVideo.normalized(): PortabilityVideo = copy(
    url = url.trim(),
    title = title.trim(),
    thumbnailUrl = thumbnailUrl.trim(),
    durationSeconds = durationSeconds.coerceAtLeast(0L),
    channelName = channelName.trim(),
    channelUrl = channelUrl.trim(),
    channelAvatarUrl = channelAvatarUrl.trim(),
    viewCount = viewCount.coerceAtLeast(0L),
)
