package dev.typetype.server.services

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.PlaylistVideoItem

object YoutubeTypeTypeMapper {
    fun playlistVideo(video: PlaylistVideoItem, historyByVideoId: Map<String, HistoryItem>): PlaylistVideoItem {
        val id = videoId(video.url)
        val history = id?.let { historyByVideoId[it] }
        return video.copy(
            title = video.title.ifBlank { history?.title.orEmpty() }.ifBlank { fallbackTitle(id, video.url) },
            thumbnail = video.thumbnail.ifBlank { history?.thumbnail.orEmpty() }.ifBlank { thumbnailForId(id) },
            duration = video.duration.takeIf { it > 0 } ?: history?.duration ?: 0,
            channelName = video.channelName.ifBlank { history?.channelName.orEmpty() },
            channelUrl = video.channelUrl.ifBlank { history?.channelUrl.orEmpty() },
            channelAvatar = video.channelAvatar.ifBlank { history?.channelAvatar.orEmpty() },
        )
    }

    fun historyItem(item: HistoryItem): HistoryItem {
        val id = videoId(item.url)
        return item.copy(
            title = item.title.ifBlank { fallbackTitle(id, item.url) },
            thumbnail = item.thumbnail.ifBlank { thumbnailForId(id) },
        )
    }

    fun thumbnailForUrl(url: String): String = thumbnailForId(videoId(url))

    fun titleForUrl(url: String): String = fallbackTitle(videoId(url), url)

    fun videoId(url: String): String? {
        val trimmed = url.trim()
        WATCH_ID_REGEX.find(trimmed)?.let { return it.groupValues[1] }
        SHORTS_ID_REGEX.find(trimmed)?.let { return it.groupValues[1] }
        LIVE_ID_REGEX.find(trimmed)?.let { return it.groupValues[1] }
        SHORT_URL_REGEX.find(trimmed)?.let { return it.groupValues[1] }
        return trimmed.takeIf { RAW_ID_REGEX.matches(it) }
    }

    private fun fallbackTitle(id: String?, url: String): String = id?.let { "YouTube video $it" } ?: url

    private fun thumbnailForId(id: String?): String = id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()

    private val WATCH_ID_REGEX = Regex("""[?&]v=([A-Za-z0-9_-]{6,})""")
    private val SHORTS_ID_REGEX = Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{6,})""", RegexOption.IGNORE_CASE)
    private val LIVE_ID_REGEX = Regex("""youtube\.com/live/([A-Za-z0-9_-]{6,})""", RegexOption.IGNORE_CASE)
    private val SHORT_URL_REGEX = Regex("""youtu\.be/([A-Za-z0-9_-]{6,})""", RegexOption.IGNORE_CASE)
    private val RAW_ID_REGEX = Regex("""^[A-Za-z0-9_-]{6,}$""")
}
