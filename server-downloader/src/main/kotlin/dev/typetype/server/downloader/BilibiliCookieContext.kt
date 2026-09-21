package dev.typetype.server.downloader

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object BilibiliCookieContext {
    @Volatile private var cookieHeader: String? = null

    fun set(value: String?) {
        cookieHeader = value?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun headerFor(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val host = parsed.host.lowercase()
        val isBilibiliApi = host == "bilibili.com" || host.endsWith(".bilibili.com")
        return cookieHeader?.takeIf { parsed.isHttps && isBilibiliApi }
    }
}
