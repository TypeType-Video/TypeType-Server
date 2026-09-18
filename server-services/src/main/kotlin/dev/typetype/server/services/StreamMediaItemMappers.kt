package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.VideoStreamItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun VideoStream.toVideoStreamItem(videoId: String, isVideoOnly: Boolean): VideoStreamItem {
    val method = deliveryMethodName()
    return VideoStreamItem(
        url = playableUrl(method),
        mimeType = getFormat()?.getMimeType() ?: "",
        format = getFormat()?.name ?: "",
        resolution = getResolution(),
        bitrate = getBitrate().takeIf { it > 0 },
        codec = getCodec()?.takeIf { it.isNotBlank() },
        isVideoOnly = isVideoOnly,
        itag = getItag(),
        width = getWidth(),
        height = getHeight(),
        fps = getFps(),
        contentLength = getItagItem()?.getContentLength() ?: 0L,
        initStart = getInitStart().toLong(),
        initEnd = getInitEnd().toLong(),
        indexStart = getIndexStart().toLong(),
        indexEnd = getIndexEnd().toLong(),
        deliveryMethod = method,
        manifestUrl = sabrManifestUrl(videoId, method),
        sabrSessionUrl = if (method == "sabr") "/sabr/session/$videoId?videoItag=${getItag()}" else null,
    )
}

fun AudioStream.toAudioStreamItem(videoId: String): AudioStreamItem {
    val method = deliveryMethodName()
    return AudioStreamItem(
        url = playableUrl(method),
        mimeType = getFormat()?.getMimeType() ?: "",
        format = getFormat()?.name ?: "",
        bitrate = averageBitrate.takeIf { it > 0 },
        codec = getCodec()?.takeIf { it.isNotBlank() },
        quality = getQuality(),
        itag = getItag(),
        contentLength = getItagItem()?.getContentLength() ?: 0L,
        initStart = getInitStart().toLong(),
        initEnd = getInitEnd().toLong(),
        indexStart = getIndexStart().toLong(),
        indexEnd = getIndexEnd().toLong(),
        audioTrackId = getAudioTrackId(),
        audioTrackName = getAudioTrackName(),
        audioLocale = getAudioLocale(),
        isOriginal = false,
        deliveryMethod = method,
        manifestUrl = sabrManifestUrl(videoId, method),
        sabrSessionUrl = if (method == "sabr") audioSessionUrl(videoId, getItag(), getAudioTrackId()) else null,
    )
}

private fun VideoStream.deliveryMethodName(): String = deliveryMethodName(getDeliveryMethod().name)

private fun AudioStream.deliveryMethodName(): String = deliveryMethodName(getDeliveryMethod().name)

private fun deliveryMethodName(name: String): String = when (name) {
    "PROGRESSIVE_HTTP" -> "progressive"
    "DASH" -> "dash"
    "HLS" -> "hls"
    "SABR" -> "sabr"
    else -> "progressive"
}

private fun VideoStream.playableUrl(method: String): String =
    if (method == "sabr" || !isUrl) "" else getContent().orEmpty()

private fun AudioStream.playableUrl(method: String): String =
    if (method == "sabr" || !isUrl) "" else getContent().orEmpty()

private fun sabrManifestUrl(videoId: String, method: String): String? =
    if (method == "sabr") "/sabr/manifest/$videoId" else null

private fun audioSessionUrl(videoId: String, itag: Int, trackId: String?): String {
    val track = trackId?.takeIf { it.isNotBlank() }
        ?.let { "&audioTrackId=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }
        .orEmpty()
    return "/sabr/session/$videoId?audioItag=$itag$track"
}

fun VideoStreamItem.isSupportedPlaybackStream(): Boolean =
    deliveryMethod != "sabr" || isSupportedSabrVideo()

fun AudioStreamItem.isSupportedPlaybackStream(): Boolean =
    deliveryMethod != "sabr" || isSupportedSabrAudio()

private fun VideoStreamItem.isSupportedSabrVideo(): Boolean =
    videoCodec().let { codec ->
        codec.contains("avc1") || codec.contains("vp9") || codec.contains("vp09") || codec.contains("av01")
    }

private fun AudioStreamItem.isSupportedSabrAudio(): Boolean =
    mimeType.contains("mp4") && (codec?.contains("mp4a") == true || mimeType.contains("mp4a"))

private fun VideoStreamItem.videoCodec(): String = listOfNotNull(codec, mimeType).joinToString(" ").lowercase()
