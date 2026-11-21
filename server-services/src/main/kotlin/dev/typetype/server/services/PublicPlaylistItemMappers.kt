package dev.typetype.server.services

import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.PublicPlaylistResponse
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

private val BILIBILI_PLAYLIST_MID_REGEX = Regex(
    """^(?:https?://(?:www\.|m\.)?bilibili\.com/medialist/play/(\d+)|https?://space\.bilibili\.com/(\d+)/lists/\d+|bilibili:(?:season|series):(\d+):)""",
    RegexOption.IGNORE_CASE,
)

fun bilibiliPlaylistUploaderUrl(requestUrl: String, explicitUploaderUrl: String = ""): String {
    if (explicitUploaderUrl.isNotBlank()) return ChannelUrlCanonicalizer.canonicalize(explicitUploaderUrl)
    val match = BILIBILI_PLAYLIST_MID_REGEX.find(requestUrl.trim()) ?: return ""
    val mid = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return ""
    return "https://space.bilibili.com/$mid"
}

fun PublicPlaylistResponse.withFallbackUploaderUrl(
    requestUrl: String,
    explicitUploaderUrl: String = "",
): PublicPlaylistResponse {
    if (recommendationServiceId(requestUrl) != BILIBILI_SERVICE_ID) return this
    val fallback = bilibiliPlaylistUploaderUrl(requestUrl, explicitUploaderUrl)
    if (fallback.isBlank() || videos.all { it.uploaderUrl.isNotBlank() }) return this
    return copy(videos = videos.map { if (it.uploaderUrl.isNotBlank()) it else it.copy(uploaderUrl = fallback) })
}

fun PlaylistInfo.toPublicPlaylistItem(): PublicPlaylistItem = PublicPlaylistItem(
    id = url ?: "",
    title = name ?: "",
    url = url ?: "",
    thumbnailUrl = thumbnailUrl.toAbsoluteUrl(),
    uploaderName = uploaderName ?: "",
    streamCount = streamCount,
    playlistType = when {
        url?.contains("business=space_collection") == true -> "collection"
        url?.contains("business=space_series") == true -> "series"
        else -> playlistType?.name?.lowercase() ?: ""
    },
)

fun emptyPublicPlaylistItem(url: String): PublicPlaylistItem = PublicPlaylistItem(
    id = url,
    title = "",
    url = url,
    thumbnailUrl = "",
    uploaderName = "",
    streamCount = -1L,
    playlistType = "",
)
