package dev.typetype.server.services

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URI

internal suspend fun rewriteProviderHlsManifest(
    manifest: String,
    baseUrl: String,
    mapUrl: suspend (String) -> String,
): String = coroutineScope {
    val base = URI(baseUrl)
    val uriAttr = Regex("""URI="([^"]+)"""")
    val lines = manifest.lines()
    val references = lines.flatMap { line ->
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> emptyList()
            trimmed.startsWith("#") -> uriAttr.findAll(trimmed).map { it.groupValues[1] }.toList()
            else -> listOf(trimmed)
        }
    }
    val resolvedReferences = references.map { raw -> resolveProviderManifestUrl(base, raw) }
        .filter { it.isHttpUrl() }
        .distinct()
    val mappedReferences = resolvedReferences
        .chunked(MAX_CONCURRENT_MAPPINGS)
        .flatMap { batch -> batch.map { target -> async { target to mapUrl(target) } }.awaitAll() }
        .toMap()

    fun mapResolved(raw: String): String {
        val resolved = resolveProviderManifestUrl(base, raw)
        return if (resolved.isHttpUrl()) mappedReferences[resolved] ?: resolved else resolved
    }

    val rewritten = ArrayList<String>()
    for (line in lines) {
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
    rewritten.joinToString("\n")
}

private fun resolveProviderManifestUrl(base: URI, raw: String): String = runCatching {
    if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        raw
    } else {
        base.resolve(raw).toString()
    }
}.getOrDefault(raw)

private fun String.isHttpUrl(): Boolean = startsWith("http://", ignoreCase = true) ||
    startsWith("https://", ignoreCase = true)

private const val MAX_CONCURRENT_MAPPINGS = 16
