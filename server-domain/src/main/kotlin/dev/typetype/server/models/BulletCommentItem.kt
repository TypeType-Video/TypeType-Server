package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class BulletCommentItem(
    val text: String,
    val argbColor: Int,
    val position: String,
    val relativeFontSize: Double,
    val durationMs: Long,
    val isLive: Boolean,
)
