package dev.typetype.server.routes

internal sealed interface AudioOnlyByteRange {
    data class Satisfiable(val first: Long, val last: Long, val total: Long) : AudioOnlyByteRange
    data class Unsatisfiable(val total: Long) : AudioOnlyByteRange
}

internal fun parseAudioOnlyByteRange(value: String?, total: Long): AudioOnlyByteRange? {
    if (value == null || !value.startsWith("bytes=") || total < 0L) return null
    val spec = value.removePrefix("bytes=").trim()
    if (spec.contains(',')) return null
    val parts = spec.split("-", limit = 2)
    if (parts.size != 2) return null
    val first = parts[0]
    val last = parts[1]
    val range = when {
        first.isBlank() -> suffixRange(last, total)
        else -> explicitRange(first, last, total)
    }
    return range ?: AudioOnlyByteRange.Unsatisfiable(total)
}

private fun suffixRange(value: String, total: Long): AudioOnlyByteRange.Satisfiable? {
    val suffixLength = value.toLongOrNull()?.takeIf { it > 0L } ?: return null
    val first = (total - suffixLength).coerceAtLeast(0L)
    return AudioOnlyByteRange.Satisfiable(first, total - 1L, total)
}

private fun explicitRange(firstValue: String, lastValue: String, total: Long): AudioOnlyByteRange.Satisfiable? {
    val first = firstValue.toLongOrNull()?.coerceAtLeast(0L) ?: return null
    if (first >= total) return null
    val last = lastValue.toLongOrNull()?.coerceAtMost(total - 1L) ?: total - 1L
    if (first > last) return null
    return AudioOnlyByteRange.Satisfiable(first, last, total)
}
