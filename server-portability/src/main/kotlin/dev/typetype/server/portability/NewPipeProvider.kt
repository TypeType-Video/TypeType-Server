package dev.typetype.server.portability

import java.net.URI

object NewPipeProvider {
    fun serviceId(url: String): Int? {
        val host = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull() ?: return null
        return when {
            host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com") -> 0
            host == "bilibili.com" || host.endsWith(".bilibili.com") || host == "b23.tv" -> 5
            host == "nicovideo.jp" || host.endsWith(".nicovideo.jp") || host == "nico.ms" -> 6
            else -> null
        }
    }

    fun supported(url: String, target: NewPipeArchiveTarget): Boolean {
        val id = serviceId(url) ?: return false
        return target.pipePipe || id == 0
    }
}
