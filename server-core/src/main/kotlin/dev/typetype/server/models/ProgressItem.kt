package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ProgressItem(
    val videoUrl: String = "",
    val position: Long,
    val updatedAt: Long = 0L,
)
