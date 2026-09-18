package dev.typetype.server.services

import java.net.URI

object StoryboardProxyDetector {
    fun isYouTubeStoryboard(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.lowercase() ?: return false
        return host == "i.ytimg.com" && path.startsWith("/sb/")
    }
}
