package dev.typetype.server.services

import java.net.URI

object ChannelUrlCanonicalizer {
    fun canonicalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return trimmed
        val path = normalizePath(uri.path)
        val port = normalizePort(uri.port)
        return runCatching {
            URI(
                "https",
                uri.userInfo,
                host,
                port,
                path,
                null,
                null,
            ).toString()
        }.getOrDefault(trimmed)
    }

    private fun normalizePath(rawPath: String?): String {
        if (rawPath.isNullOrBlank()) return ""
        var path: String = rawPath
        while (path.length > 1 && path.endsWith('/')) {
            path = path.dropLast(1)
        }
        return if (path == "/") "" else path
    }

    private fun normalizePort(rawPort: Int): Int {
        if (rawPort == 80 || rawPort == 443) return -1
        return rawPort
    }
}
