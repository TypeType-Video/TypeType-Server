package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem

fun hlsFallbackStream(url: String): AudioStreamItem = AudioStreamItem(
    url = url,
    mimeType = "application/vnd.apple.mpegurl",
    format = "HLS",
    bitrate = null,
    codec = "hls",
    quality = "HLS",
    itag = HLS_FALLBACK_ITAG,
    contentLength = 0,
    initStart = 0,
    initEnd = 0,
    indexStart = 0,
    indexEnd = 0,
    audioTrackId = null,
    audioTrackName = null,
    audioLocale = null,
    isOriginal = false,
)

private const val HLS_FALLBACK_ITAG = -1
