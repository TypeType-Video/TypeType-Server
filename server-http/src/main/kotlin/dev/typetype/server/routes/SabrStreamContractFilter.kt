package dev.typetype.server.routes

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.withSabrManifestUrls
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal suspend fun StreamResponse.withPlayableSabrStreams(
    url: String,
    sabrSessionStore: SabrSessionStore,
): StreamResponse {
    if (!hasSabrStreams()) return this
    val videoId = url.youtubeVideoId() ?: return this
    val prepared = sabrSessionStore.fetchInfo(videoId, cachedFirst = true)
        ?: return withoutSabrStreams()
    val hasAudio = SabrFormatSelector.audio(prepared.info, null, null, requireAac = false) != null
    if (!hasAudio) return withoutSabrStreams()
    val enriched = withMissingSabrVideoStreams(videoId, prepared.info)
        .withMissingSabrAudioStreams(videoId, prepared.info)
    return enriched.copy(
        videoStreams = enriched.videoStreams.filter { it.isPlayableSabrVideo(prepared.info) },
        videoOnlyStreams = enriched.videoOnlyStreams.filter { it.isPlayableSabrVideo(prepared.info) },
        audioStreams = enriched.audioStreams.filter { it.isPlayableSabrAudio(prepared.info) },
    )
}

private fun StreamResponse.withMissingSabrVideoStreams(videoId: String, info: YoutubeSabrInfo): StreamResponse {
    val existing = (videoStreams + videoOnlyStreams).map { it.itag }.toSet()
    val missing = info.formats.asSequence()
        .filter { it.isVideo && it.itag !in existing }
        .mapNotNull { it.toVideoStreamItem(videoId, info) }
        .toList()
    if (missing.isEmpty()) return this
    return copy(videoOnlyStreams = videoOnlyStreams + missing)
}

private fun StreamResponse.withMissingSabrAudioStreams(videoId: String, info: YoutubeSabrInfo): StreamResponse {
    val existing = audioStreams.map { it.itag to it.audioTrackId }.toSet()
    val missing = info.formats.asSequence()
        .filter { it.isAudio && (it.itag to it.audioTrackId) !in existing }
        .filter { SabrFormatSelector.audio(info, it.itag, it.audioTrackId, requireAac = false) != null }
        .mapNotNull { it.toAudioStreamItem(videoId) }
        .toList()
    if (missing.isEmpty()) return this
    return copy(audioStreams = audioStreams + missing)
}

internal fun StreamResponse.hasSabrStreams(): Boolean =
    videoStreams.any { it.deliveryMethod == SABR_DELIVERY_METHOD } ||
        videoOnlyStreams.any { it.deliveryMethod == SABR_DELIVERY_METHOD } ||
        audioStreams.any { it.deliveryMethod == SABR_DELIVERY_METHOD }

internal fun StreamResponse.withoutSabrStreams(): StreamResponse = copy(
    videoStreams = videoStreams.filterNot { it.deliveryMethod == SABR_DELIVERY_METHOD },
    videoOnlyStreams = videoOnlyStreams.filterNot { it.deliveryMethod == SABR_DELIVERY_METHOD },
    audioStreams = audioStreams.filterNot { it.deliveryMethod == SABR_DELIVERY_METHOD },
)

internal fun StreamResponse.onlyLiveHls(): StreamResponse = copy(
    dashMpdUrl = "",
    videoStreams = emptyList(),
    videoOnlyStreams = emptyList(),
    audioStreams = emptyList(),
    hasLiveManifest = hlsUrl.isNotBlank(),
)

internal fun StreamResponse.onlySabrStreams(): StreamResponse = copy(
    hlsUrl = "",
    dashMpdUrl = "",
    videoStreams = videoStreams.filter { it.deliveryMethod == SABR_DELIVERY_METHOD },
    videoOnlyStreams = videoOnlyStreams.filter { it.deliveryMethod == SABR_DELIVERY_METHOD },
    audioStreams = audioStreams.filter { it.deliveryMethod == SABR_DELIVERY_METHOD },
)

private fun VideoStreamItem.isPlayableSabrVideo(info: YoutubeSabrInfo): Boolean =
    deliveryMethod != SABR_DELIVERY_METHOD || SabrFormatSelector.video(info, itag) != null

private fun AudioStreamItem.isPlayableSabrAudio(info: YoutubeSabrInfo): Boolean =
    deliveryMethod != SABR_DELIVERY_METHOD ||
    SabrFormatSelector.audio(info, itag, audioTrackId, requireAac = false) != null

private fun YoutubeSabrFormat.toVideoStreamItem(videoId: String, info: YoutubeSabrInfo): VideoStreamItem? {
    if (SabrFormatSelector.video(info, itag) == null) return null
    val mime = mimeType?.takeIf { it.isNotBlank() } ?: return null
    val container = mime.substringBefore(';').trim()
    return VideoStreamItem(
        url = "",
        mimeType = container,
        format = container.formatName(),
        resolution = qualityLabel ?: heightLabel(),
        bitrate = bitrate.takeIf { it > 0 },
        codec = mime.codec(),
        isVideoOnly = true,
        itag = itag,
        width = width.coerceAtLeast(0),
        height = height.coerceAtLeast(0),
        fps = 0,
        contentLength = contentLength.coerceAtLeast(0L),
        initStart = 0L,
        initEnd = 0L,
        indexStart = 0L,
        indexEnd = 0L,
        deliveryMethod = SABR_DELIVERY_METHOD,
        manifestUrl = "/sabr/manifest/$videoId",
        sabrSessionUrl = "/sabr/session/$videoId?videoItag=$itag",
    )
}

private fun YoutubeSabrFormat.toAudioStreamItem(videoId: String): AudioStreamItem? {
    val mime = mimeType?.takeIf { it.isNotBlank() } ?: return null
    val container = mime.substringBefore(';').trim()
    val track = audioTrackId?.takeIf { it.isNotBlank() }
        ?.let { "&audioTrackId=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }
        .orEmpty()
    return AudioStreamItem(
        url = "",
        mimeType = container,
        format = container.substringAfter('/').uppercase(),
        bitrate = bitrate.takeIf { it > 0 },
        codec = mime.codec(),
        quality = audioQuality,
        itag = itag,
        contentLength = contentLength.coerceAtLeast(0L),
        initStart = 0L,
        initEnd = 0L,
        indexStart = 0L,
        indexEnd = 0L,
        audioTrackId = audioTrackId,
        audioTrackName = audioTrackDisplayName,
        audioLocale = audioTrackId?.substringBefore('.'),
        isOriginal = isOriginalAudio,
        deliveryMethod = SABR_DELIVERY_METHOD,
        manifestUrl = "/sabr/manifest/$videoId",
        sabrSessionUrl = "/sabr/session/$videoId?audioItag=$itag$track",
    )
}

private fun YoutubeSabrFormat.heightLabel(): String = height.takeIf { it > 0 }?.let { "${it}p" } ?: ""

private fun String.formatName(): String = when (lowercase()) {
    "video/webm" -> "WEBM"
    "video/mp4" -> "MPEG_4"
    else -> substringAfter('/').uppercase()
}

private fun String.codec(): String? {
    val marker = "codecs="
    val start = indexOf(marker)
    if (start < 0) return null
    return substring(start + marker.length)
        .substringBefore(';')
        .trim()
        .trim('"')
        .takeIf { it.isNotBlank() }
}

private const val SABR_DELIVERY_METHOD = "sabr"

internal fun StreamResponse.forSabrPlayback(): StreamResponse =
    if (isLive) onlyLiveHls() else withSabrManifestUrls().onlySabrStreams()
