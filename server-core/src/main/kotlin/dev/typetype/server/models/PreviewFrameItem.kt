package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PreviewFrameItem(
    val urls: List<String>,
    val frameWidth: Int,
    val frameHeight: Int,
    val totalCount: Int,
    val durationPerFrame: Int,
    val framesPerPageX: Int,
    val framesPerPageY: Int,
)
