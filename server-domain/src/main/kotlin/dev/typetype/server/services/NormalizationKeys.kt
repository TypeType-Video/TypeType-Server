package dev.typetype.server.services

import java.text.Normalizer
import java.net.URI

fun normalizeChannelKey(value: String): String = value.trim()
    .substringBefore('#')
    .substringBefore('?')
    .removeSuffix("/")
    .replace("http://", "https://")
    .replace(
        Regex("^https://(?:www\\.|m\\.|music\\.)youtube\\.com", RegexOption.IGNORE_CASE),
        "https://youtube.com",
    )
    .withoutYoutubeTab()

private fun String.withoutYoutubeTab(): String {
    val uri = runCatching { URI(this) }.getOrNull() ?: return this
    if (!uri.host.equals("youtube.com", ignoreCase = true)) return this
    val segments = uri.path.split('/').filter(String::isNotBlank)
    if (segments.size < 2 || segments.last().lowercase() !in YOUTUBE_CHANNEL_TABS) return this
    val path = "/${segments.dropLast(1).joinToString("/")}"
    return URI(uri.scheme, uri.userInfo, uri.host, uri.port, path, null, null).toString()
}

private val YOUTUBE_CHANNEL_TABS = setOf(
    "featured",
    "videos",
    "shorts",
    "streams",
    "playlists",
    "community",
    "about",
)

fun normalizePlaylistKey(value: String): String = value.trim()
    .substringBefore('#')
    .removeSuffix("/")
    .replace("http://", "https://")

fun normalizeBlockedKeyword(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC).trim().lowercase()
