package dev.typetype.server.services

import java.net.URLEncoder
import java.net.URI
import java.nio.charset.StandardCharsets

fun isManifestUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (uri.scheme != "http" && uri.scheme != "https") return false
    if (uri.path.contains("/file/seg.ts")) return false
    val host = uri.host?.lowercase().orEmpty()
    return host == "manifest.googlevideo.com" || uri.path.endsWith(".m3u8", ignoreCase = true)
}

fun rewriteYouTubeHlsManifest(
    manifest: String,
    mapManifestUrl: (String) -> String = ::toHlsProxyUrl,
): String {
    val uriAttr = Regex("""URI="([^"]+)"""")
    return manifest.lines().joinToString("\n") { line ->
        val t = line.trim()
        when {
            t.isBlank() -> line
            t.startsWith("#") -> uriAttr.replace(t) { mr -> """URI="${toHlsUrl(mr.groupValues[1], mapManifestUrl)}""" }
            else -> toHlsUrl(t, mapManifestUrl)
        }
    }
}

fun toHlsProxyUrl(url: String): String {
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8)
    return if (isManifestUrl(url)) "hls-manifest?url=$encoded" else "../proxy?url=$encoded"
}

private fun toHlsUrl(url: String, mapManifestUrl: (String) -> String): String =
    if (isManifestUrl(url)) mapManifestUrl(url) else toHlsProxyUrl(url)
