package dev.typetype.server.services

import dev.typetype.server.models.StreamResponse
import kotlinx.serialization.Serializable

@Serializable
data class FocusVideoSnapshot(
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val uploaderUrl: String,
    val uploaderName: String,
    val duration: Long,
    val publishedAt: Long? = null,
    val isLive: Boolean = false,
)

internal fun StreamResponse.toFocusVideoSnapshot(): FocusVideoSnapshot = FocusVideoSnapshot(
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    uploaderUrl = uploaderUrl,
    uploaderName = uploaderName,
    duration = duration,
    publishedAt = publishedAt ?: uploaded.takeIf { it > 0 },
    isLive = isLive || isLiveContent,
)
