package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class BoundedExpiringCacheTest {
    @Test
    fun `reads expire entries independently of access order`() {
        var now = 0L
        val cache = BoundedExpiringCache<String, String>(10, ttl = Duration.ofMillis(10), clock = { now })
        cache.put("first", "1")
        now = 5L
        cache.put("second", "2")
        assertEquals("1", cache.get("first"))
        now = 10L

        assertEquals("2", cache.get("second"))
        assertNull(cache.get("first"))
        assertEquals(1, cache.size())
        assertEquals(1L, cache.weight())
        now = 15L
        assertNull(cache.get("second"))
        assertEquals(0L, cache.weight())
    }

    @Test
    fun `replacement keeps its new expiry when the original expiry passes`() {
        var now = 0L
        val cache = BoundedExpiringCache<String, String>(10, ttl = Duration.ofMillis(10), clock = { now })
        cache.put("key", "old")
        now = 5L
        cache.put("key", "new")
        now = 10L
        cache.evictExpired()
        assertEquals("new", cache.get("key"))
        now = 15L
        assertNull(cache.get("key"))
    }

    @Test
    fun `new entries honor their expiry after clear and a backward clock change`() {
        var now = 100L
        val cache = BoundedExpiringCache<String, String>(10, ttl = Duration.ofMillis(10), clock = { now })
        cache.put("old", "1")
        cache.clear()
        cache.put("later", "2")
        now = 50L
        cache.put("earlier", "3")
        now = 60L
        assertNull(cache.get("earlier"))
        assertEquals("2", cache.get("later"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `least recently used entry is removed at capacity`() {
        val cache = BoundedExpiringCache<String, String>(
            maxEntries = 2,
            ttl = Duration.ofMinutes(1),
        )
        cache.put("first", "1")
        cache.put("second", "2")
        cache.get("first")

        cache.put("third", "3")

        assertEquals("1", cache.get("first"))
        assertNull(cache.get("second"))
        assertEquals("3", cache.get("third"))
    }

    @Test
    fun `weight limit removes oldest entries`() {
        val cache = BoundedExpiringCache<String, String>(
            maxEntries = 10,
            maxWeight = 5,
            ttl = Duration.ofMinutes(1),
            weigher = { it.length.toLong() },
        )
        cache.put("first", "123")

        cache.put("second", "456")

        assertNull(cache.get("first"))
        assertEquals("456", cache.get("second"))
        assertEquals(3, cache.weight())
    }

    @Test
    fun `new writes purge expired entries without reading their keys`() {
        var now = 0L
        val cache = BoundedExpiringCache<String, String>(
            maxEntries = 10,
            ttl = Duration.ofMillis(10),
            clock = { now },
        )
        cache.put("expired", "old")
        now = 10L

        cache.put("current", "new")

        assertNull(cache.get("expired"))
        assertEquals(1, cache.size())
    }
}
