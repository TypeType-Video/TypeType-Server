package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AudioOnlyStreamResponse(
    val src: String,
    val kind: String,
    val mimeType: String,
    val codec: String?,
    val bitrate: Int?,
    val contentLength: Long?,
    val duration: Long?,
)
