package dev.typetype.server.services

import dev.typetype.server.models.VideoItem
import java.net.URI

object RssVideoMetadata {
    fun serviceId(video: VideoItem): Int {
        val host = runCatching { URI(video.url).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            host == "b23.tv" || host == "bilibili.com" || host.endsWith(".bilibili.com") -> 5
            host == "nico.ms" || host == "nicovideo.jp" || host.endsWith(".nicovideo.jp") -> 6
            else -> 0
        }
    }

    fun publishedAtMillis(video: VideoItem): Long {
        val value = video.publishedAt?.takeIf { it > 0 } ?: video.uploaded.takeIf { it > 0 } ?: 0L
        return if (value in 1..9_999_999_999L) value * 1_000L else value
    }
}
