package dev.typetype.server.services

class SabrMemorySegmentCache(private val maxBytes: Long) {
    private val segments = LinkedHashMap<String, CachedSabrSegment>(64, 0.75f, true)
    private var bytes = 0L

    @Synchronized
    fun get(key: String): CachedSabrSegment? = segments[key]

    @Synchronized
    fun put(key: String, segment: CachedSabrSegment): Unit {
        segments.remove(key)?.let { bytes -= it.length }
        segments[key] = segment
        bytes += segment.length
        trim()
    }

    @Synchronized
    fun evictBefore(ms: Long): Unit {
        val iterator = segments.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val segment = entry.value
            if (!segment.init && segment.startMs + segment.durationMs < ms) {
                bytes -= segment.length
                iterator.remove()
            }
        }
    }

    @Synchronized
    private fun trim(): Unit {
        val iterator = segments.iterator()
        while (bytes > maxBytes && iterator.hasNext()) {
            val segment = iterator.next().value
            bytes -= segment.length
            iterator.remove()
        }
    }
}
