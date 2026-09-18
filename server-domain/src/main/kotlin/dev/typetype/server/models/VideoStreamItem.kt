package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class VideoStreamItem(
    val url: String,
    val mimeType: String,
    val format: String,
    val resolution: String,
    val bitrate: Int?,
    val codec: String?,
    val isVideoOnly: Boolean,
    val itag: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val contentLength: Long,
    val initStart: Long,
    val initEnd: Long,
    val indexStart: Long,
    val indexEnd: Long,
    val deliveryMethod: String = "progressive",
    val manifestUrl: String? = null,
    val sabrSessionUrl: String? = null,
)
