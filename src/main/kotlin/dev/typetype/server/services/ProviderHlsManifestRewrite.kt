package dev.typetype.server.services

import java.net.URI

internal suspend fun rewriteProviderHlsManifest(
    manifest: String,
    baseUrl: String,
    mapUrl: suspend (String) -> String,
): String {
    val base = URI(baseUrl)
    val uriAttr = Regex("""URI="([^"]+)"""")
    suspend fun mapResolved(raw: String): String {
        val resolved = runCatching {
            if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
                raw
            } else {
                base.resolve(raw).toString()
            }
        }.getOrDefault(raw)
        return if (resolved.startsWith("http://", ignoreCase = true) ||
            resolved.startsWith("https://", ignoreCase = true)
        ) mapUrl(resolved) else resolved
    }

    val rewritten = ArrayList<String>()
    for (line in manifest.lines()) {
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> rewritten += line
            trimmed.startsWith("#") -> {
                val builder = StringBuilder(trimmed)
                val matches = uriAttr.findAll(trimmed).toList()
                for (match in matches.asReversed()) {
                    val mapped = mapResolved(match.groupValues[1])
                    builder.replace(match.range.first, match.range.last + 1, "URI=\"$mapped\"")
                }
                rewritten += builder.toString()
            }
            else -> rewritten += mapResolved(trimmed)
        }
    }
    return rewritten.joinToString("\n")
}
