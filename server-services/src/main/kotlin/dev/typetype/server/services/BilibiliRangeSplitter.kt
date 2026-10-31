package dev.typetype.server.services

const val MAX_BILIBILI_SUBCHUNK_SIZE = 1_000_000L

data class ByteRange(val start: Long, val end: Long) {
    val length: Long get() = end - start + 1

    override fun toString(): String = "$start-$end"
}

object BilibiliRangeSplitter {

    /**
     * Parses a standard `Range: bytes=start-end` or `bytes=start-` header.
     * Returns null if header is missing, malformed, or contains multiple range sets.
     */
    fun parseRange(rangeHeader: String?): Pair<Long?, Long?>? {
        if (rangeHeader == null) return null
        val trimmed = rangeHeader.trim()
        if (!trimmed.startsWith("bytes=", ignoreCase = true)) return null
        val eqIdx = trimmed.indexOf('=')
        val spec = trimmed.substring(eqIdx + 1).trim()
        if (spec.contains(",")) return null
        val dashIdx = spec.indexOf('-')
        if (dashIdx < 0) return null
        val startStr = spec.substring(0, dashIdx).trim()
        val endStr = spec.substring(dashIdx + 1).trim()
        val start = if (startStr.isNotEmpty()) startStr.toLongOrNull() else null
        val end = if (endStr.isNotEmpty()) endStr.toLongOrNull() else null
        if (start == null && end == null) return null
        return Pair(start, end)
    }

    /**
     * Splits [start..end] into slices of at most [maxChunkSize] bytes.
     * Guaranteed that every slice has length <= maxChunkSize.
     */
    fun splitRange(start: Long, end: Long, maxChunkSize: Long = MAX_BILIBILI_SUBCHUNK_SIZE): List<ByteRange> {
        require(start >= 0) { "start ($start) must be non-negative" }
        require(start <= end) { "start ($start) must be <= end ($end)" }
        require(maxChunkSize > 0) { "maxChunkSize ($maxChunkSize) must be positive" }

        val ranges = mutableListOf<ByteRange>()
        var current = start
        while (current <= end) {
            val chunkEnd = minOf(current + maxChunkSize - 1, end)
            ranges.add(ByteRange(current, chunkEnd))
            current = chunkEnd + 1
        }
        return ranges
    }

    /**
     * Parses the total instance-length from Content-Range header, e.g. "bytes 0-999999/52428800".
     * Returns null if total is "*" or unparseable.
     */
    fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange == null) return null
        val slashIdx = contentRange.lastIndexOf('/')
        if (slashIdx < 0 || slashIdx >= contentRange.length - 1) return null
        val totalPart = contentRange.substring(slashIdx + 1).trim()
        return totalPart.toLongOrNull()
    }
}
