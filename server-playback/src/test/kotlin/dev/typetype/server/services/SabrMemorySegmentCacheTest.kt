package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SabrMemorySegmentCacheTest {
    @Test
    fun `eviction behind reader tail keeps the boundary and newer media`() {
        val cache = SabrMemorySegmentCache(maxBytes = 1_000)
        cache.put("before-tail", segment(startMs = 0L, durationMs = 5L))
        cache.put("at-tail", segment(startMs = 5L, durationMs = 5L))
        cache.put("ahead", segment(startMs = 10L, durationMs = 5L))

        cache.evictBefore(6L)

        assertNull(cache.get("before-tail"))
        assertNotNull(cache.get("at-tail"))
        assertNotNull(cache.get("ahead"))
    }

    @Test
    fun `least recently used media is removed when the byte limit is reached`() {
        val cache = SabrMemorySegmentCache(maxBytes = 10L)
        cache.put("oldest", segment(bytes = ByteArray(6)))
        cache.put("newest", segment(bytes = ByteArray(6)))

        assertNull(cache.get("oldest"))
        assertNotNull(cache.get("newest"))
    }

    private fun segment(
        startMs: Long = 0L,
        durationMs: Long = 1L,
        bytes: ByteArray = ByteArray(1),
    ): CachedSabrSegment = CachedSabrSegment(
        itag = 137,
        sequence = 1,
        init = false,
        startMs = startMs,
        durationMs = durationMs,
        mimeType = "video/webm",
        bytes = bytes,
    )
}
