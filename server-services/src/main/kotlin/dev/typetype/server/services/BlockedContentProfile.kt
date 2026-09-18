package dev.typetype.server.services

import dev.typetype.server.models.BlockedItem
import dev.typetype.server.models.BlockedKeywordItem

data class BlockedContentProfile(
    val videos: List<BlockedItem>,
    val channels: List<BlockedItem>,
    val keywords: List<BlockedKeywordItem>,
) {
    fun allowsVideo(url: String, title: String, uploaderUrl: String, uploaderName: String): Boolean =
        !blocksVideo(url) &&
            keywords.none { containsBlockedKeyword(title, it.keyword) } &&
            !blocksChannel(uploaderUrl, uploaderName)

    fun allowsRequestedVideo(url: String, uploaderUrl: String, uploaderName: String): Boolean =
        !blocksVideo(url) && !blocksChannel(uploaderUrl, uploaderName)

    fun blocksVideo(url: String): Boolean {
        val normalized = normalizeBlockedVideoKey(url)
        return normalized.isNotBlank() && videos.any { normalizeBlockedVideoKey(it.url) == normalized }
    }

    fun blocksChannel(url: String, name: String): Boolean = channels.any { item ->
        val blockedUrl = normalizeChannelKey(item.url)
        val blockedName = normalizeBlockedKeyword(item.name.orEmpty())
        blockedUrl.isNotBlank() && blockedUrl == normalizeChannelKey(url) ||
            blockedName.isNotBlank() && blockedName == normalizeBlockedKeyword(name)
    }

    fun allowsChannel(url: String, name: String): Boolean = !blocksChannel(url, name)

    companion object {
        val empty = BlockedContentProfile(videos = emptyList(), channels = emptyList(), keywords = emptyList())
    }
}

private val YOUTUBE_VIDEO_ID =
    Regex("(?:[?&]v=|/(?:shorts|embed|live)/|youtu\\.be/)([A-Za-z0-9_-]{6,})", RegexOption.IGNORE_CASE)

fun normalizeBlockedVideoKey(value: String): String {
    val trimmed = value.trim()
    val youtubeId = YOUTUBE_VIDEO_ID.find(trimmed)?.groupValues?.get(1)
    return youtubeId?.let { "youtube:video:$it" } ?: normalizeChannelKey(trimmed)
}
