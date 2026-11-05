package dev.typetype.server

import dev.typetype.server.services.ProviderMediaStartupCache
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderMediaStartupCacheTest {
    private val prefix = ByteArray(1808) { (it % 251).toByte() }

    private fun prefixEntry() = ProviderMediaStartupCache.Entry(prefix, 9_000_000L, "video/mp4", complete = false)

    @Test
    fun `serves a range inside the stored prefix`() {
        val cache = ProviderMediaStartupCache()
        cache.put(HANDLE, prefixEntry())

        val response = requireNotNull(cache.respond(HANDLE, "bytes=996-1807"))

        assertEquals(206, response.status)
        assertEquals("bytes 996-1807/9000000", response.contentRange)
        assertEquals(812L, response.contentLength)
        assertArrayEquals(prefix.copyOfRange(996, 1808), response.stream.readBytes())
    }

    @Test
    fun `leaves ranges beyond the prefix to the upstream`() {
        val cache = ProviderMediaStartupCache()
        cache.put(HANDLE, prefixEntry())

        assertNull(cache.respond(HANDLE, "bytes=1808-90000"))
        assertNull(cache.respond(HANDLE, "bytes=1000-"))
        assertNull(cache.respond(HANDLE, null))
    }

    @Test
    fun `serves a complete resource without a range`() {
        val cache = ProviderMediaStartupCache()
        val playlist = "#EXTM3U\n".toByteArray()
        cache.put(HANDLE, ProviderMediaStartupCache.Entry(playlist, null, "application/vnd.apple.mpegurl", complete = true))

        val response = requireNotNull(cache.respond(HANDLE, null))

        assertEquals(200, response.status)
        assertArrayEquals(playlist, response.stream.readBytes())
    }

    @Test
    fun `forgets entries after their lifetime`() {
        var now = 1_000L
        val cache = ProviderMediaStartupCache(ttlMillis = 100, nowMillis = { now })
        cache.put(HANDLE, prefixEntry())
        now += 101

        assertNull(cache.respond(HANDLE, "bytes=0-10"))
    }

    @Test
    fun `evicts the oldest entries when the byte budget is full`() {
        val cache = ProviderMediaStartupCache(maxBytes = 2_000)
        cache.put(HANDLE, prefixEntry())
        cache.put(OTHER_HANDLE, prefixEntry())

        assertNull(cache.get(HANDLE))
        assertNotNull(cache.get(OTHER_HANDLE))
    }

    @Test
    fun `waits for a prefetch that is already running`() = runTest {
        val cache = ProviderMediaStartupCache()
        val claim = requireNotNull(cache.begin(HANDLE))
        assertNull(cache.begin(HANDLE))
        val waiter = async { cache.awaitPending(HANDLE); cache.respond(HANDLE, "bytes=0-9") }
        yield()
        cache.put(HANDLE, prefixEntry())
        cache.finish(HANDLE, claim)

        assertTrue(waiter.await()?.status == 206)
    }

    private companion object {
        const val HANDLE = "m1_AAAAAAAAAAAAAAAAAAAAAAAA"
        const val OTHER_HANDLE = "m1_BBBBBBBBBBBBBBBBBBBBBBBB"
    }
}
