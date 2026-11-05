package dev.typetype.server.services

class ProviderMediaSegmentTable(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private val tables = object : LinkedHashMap<String, List<LongRange>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LongRange>>): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun remember(url: String, segments: List<LongRange>) {
        tables[url] = segments
    }

    @Synchronized
    fun after(url: String, start: Long): LongRange? {
        val segments = tables[url] ?: return null
        val index = segments.binarySearchBy(start) { it.first }
        return if (index >= 0) segments.getOrNull(index + 1) else null
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 512
    }
}
