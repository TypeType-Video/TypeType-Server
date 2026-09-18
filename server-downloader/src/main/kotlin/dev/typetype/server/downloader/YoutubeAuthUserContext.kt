package dev.typetype.server.downloader

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object YoutubeAuthUserContext {
    @Volatile private var value: Int? = null

    fun set(authUser: Int?): Unit {
        value = authUser
    }

    internal fun headerFor(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val host = parsed.host.lowercase()
        val isYoutube = host == "youtube.com" || host.endsWith(".youtube.com")
        return value?.toString()?.takeIf {
            isYoutube && parsed.encodedPath.startsWith("/youtubei/v1/")
        }
    }
}
