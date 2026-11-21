package dev.typetype.server.cache

import dev.typetype.server.FakeCacheService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CacheServiceDeleteMatchingTest {
    @Test
    fun `deleteMatching removes only keys matching YouTube backoff pattern`() = runTest {
        val cache = FakeCacheService()
        cache.set("youtube-related:v1:dQw4w9WgXcQ:backoff", "{}", 60)
        cache.set("youtube-related:v1:jNQXAC9IVRw:backoff", "{}", 60)
        cache.set("youtube-related:v1:dQw4w9WgXcQ", "{}", 60)
        cache.set("comments-media:v2:abc:backoff", "{}", 60)
        cache.set("channel:v2:def:backoff", "{}", 60)
        cache.set("search-v4:v2:ghi:backoff", "{}", 60)

        val deleted = cache.deleteMatching("youtube-related:v1:*:backoff")

        assertEquals(2, deleted)
        assertNull(cache.get("youtube-related:v1:dQw4w9WgXcQ:backoff"))
        assertNull(cache.get("youtube-related:v1:jNQXAC9IVRw:backoff"))
        assertNotNull(cache.get("youtube-related:v1:dQw4w9WgXcQ"))
        assertNotNull(cache.get("comments-media:v2:abc:backoff"))
        assertNotNull(cache.get("channel:v2:def:backoff"))
        assertNotNull(cache.get("search-v4:v2:ghi:backoff"))
    }

    @Test
    fun `default CacheService deleteMatching returns zero when unsupported`() = runTest {
        val bare = object : CacheService {
            override suspend fun get(key: String): String? = null
            override suspend fun set(key: String, value: String, ttlSeconds: Long) {}
            override suspend fun delete(key: String) {}
        }

        assertEquals(0, bare.deleteMatching("youtube-related:v1:*:backoff"))
    }
}
