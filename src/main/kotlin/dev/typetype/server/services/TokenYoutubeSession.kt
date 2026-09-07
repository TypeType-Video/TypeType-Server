package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrInfo

internal data class TokenYoutubeSession(
    val info: YoutubeSabrInfo,
    val token: SabrTokenBundle?,
    val title: String,
    val author: String,
    val channelId: String,
    val channelAvatarUrl: String,
    val description: String,
    val durationMs: Long,
    val viewCount: Long,
    val thumbnailUrl: String,
    val tags: List<String>,
    val isLive: Boolean,
    val isLiveContent: Boolean,
    val hlsUrl: String = "",
)

internal fun TokenYoutubeSession.preparedSabrInfo(): SabrPreparedInfo? {
    val boundToken = token?.takeIf { it.visitorData == info.visitorData } ?: return null
    return SabrPreparedInfo(info, boundToken, isLive, isLiveContent)
        .takeIf(SabrPreparedInfo::hasAudioAndVideoFormats)
}
