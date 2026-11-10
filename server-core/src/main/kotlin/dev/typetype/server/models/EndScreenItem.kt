package dev.typetype.server.models

import kotlinx.serialization.Serializable

/** Position and size are fractions of the video frame; `left`/`top` place the top left corner. */
@Serializable
data class EndScreenItem(
    val id: String,
    val type: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val startMs: Long,
    val endMs: Long,
    val left: Double,
    val top: Double,
    val width: Double,
    val aspectRatio: Double,
)
