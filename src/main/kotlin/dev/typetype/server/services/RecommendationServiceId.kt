package dev.typetype.server.services

import java.net.URI

internal fun recommendationServiceId(url: String): Int {
    val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    return when {
        host == "b23.tv" || host == "bilibili.com" || host.endsWith(".bilibili.com") -> BILIBILI_SERVICE_ID
        host == "nico.ms" || host == "nicovideo.jp" || host.endsWith(".nicovideo.jp") -> NICONICO_SERVICE_ID
        else -> YOUTUBE_SERVICE_ID
    }
}
