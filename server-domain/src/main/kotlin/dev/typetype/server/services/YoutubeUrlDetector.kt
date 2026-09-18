package dev.typetype.server.services

import java.net.URI

fun isYoutubeUrl(url: String): Boolean = runCatching {
    val host = URI(url).host?.lowercase() ?: return false
    host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
}.getOrDefault(false)
