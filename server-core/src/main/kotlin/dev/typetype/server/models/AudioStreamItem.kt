package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AudioStreamItem(
    val url: String,
    val mimeType: String,
    val format: String,
    val bitrate: Int?,
    val codec: String?,
    val quality: String?,
    val itag: Int,
    val contentLength: Long,
    val initStart: Long,
    val initEnd: Long,
    val indexStart: Long,
    val indexEnd: Long,
    val audioTrackId: String?,
    val audioTrackName: String?,
    val audioLocale: String?,
    val isOriginal: Boolean,
    val deliveryMethod: String = "progressive",
    val manifestUrl: String? = null,
    val sabrSessionUrl: String? = null,
)
