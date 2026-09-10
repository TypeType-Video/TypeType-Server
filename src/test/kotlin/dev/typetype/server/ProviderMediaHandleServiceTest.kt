package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.ProviderMediaType
import dev.typetype.server.services.rewriteProviderHlsManifest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ProviderMediaHandleServiceTest {
    @Test
    fun `provider URLs become short cache-backed paths`() = runTest {
        val service = ProviderMediaHandleService(FakeCacheService(), nowSeconds = { 1_000L })
        val raw = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=2000000000&cpn=tracking"

        val path = service.createPath(raw)
        val handle = path.removePrefix("/media/")
        val target = service.resolve(handle)

        assertTrue(path.matches(Regex("/media/m1_[A-Za-z0-9_-]{24}")))
        assertFalse(path.contains("akamaized"))
        assertEquals("https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=2000000000", target?.url)
        assertEquals(path, service.createPath(raw))
    }

    @Test
    fun `Nico cookie fragment is stored beside the opaque target`() = runTest {
        val service = ProviderMediaHandleService(FakeCacheService(), nowSeconds = { 1_000L })
        val path = service.createPath(
            "https://asset.domand.nicovideo.jp/video/01.cmfv?Expires=2000000000#cookie=domand_bid%3Dcookie123&length=10",
        )
        val target = service.resolve(path.removePrefix("/media/"))

        assertEquals("cookie123", target?.domandBid)
        assertFalse(target?.url.orEmpty().contains("#cookie"))
        assertEquals("../media/${path.substringAfterLast('/')}", service.relativeManifestPath(path))
    }

    @Test
    fun `unknown handles do not resolve`() = runTest {
        val service = ProviderMediaHandleService(FakeCacheService())
        assertNull(service.resolve("m1_invalid"))
    }

    @Test
    fun `expired provider URLs are not materialized`() = runTest {
        val service = ProviderMediaHandleService(FakeCacheService(), nowSeconds = { 2_000_000_000L })

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.createPath("https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=2000000000")
            }
        }
    }

    @Test
    fun `near expiry handles keep the remaining upstream lifetime`() = runTest {
        val cache = RecordingProviderCacheService()
        val service = ProviderMediaHandleService(cache, nowSeconds = { 1_000L })

        service.createPath("https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=1050")

        assertEquals(20L, cache.ttlSeconds)
    }

    @Test
    fun `provider HLS rewrite can map every media line`() = runTest {
        val manifest = "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\nsegment.m4s"
        val result = rewriteProviderHlsManifest(
            manifest,
            "https://upos-hz-mirrorakam.akamaized.net/path/master.m3u8",
        ) { "../media/handle" }

        assertTrue(result.contains("URI=\"../media/handle\""))
        assertTrue(result.endsWith("../media/handle"))
    }

    @Test
    fun `provider HLS rewrite maps unique references concurrently`() = runTest {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val calls = AtomicInteger()
        val manifest = buildString {
            repeat(32) {
                append("segment-$it.m4s\n")
            }
            append("segment-0.m4s")
        }

        val result = rewriteProviderHlsManifest(
            manifest,
            "https://upos-hz-mirrorakam.akamaized.net/path/master.m3u8",
        ) { target ->
            calls.incrementAndGet()
            maximum.accumulateAndGet(active.incrementAndGet()) { left, right -> maxOf(left, right) }
            delay(1)
            active.decrementAndGet()
            "../media/${target.substringAfterLast('/')}"
        }

        assertEquals(32, calls.get())
        assertTrue(maximum.get() > 1)
        assertTrue(result.contains("../media/segment-31.m4s"))
    }

    @Test
    fun `concurrent handle creation shares one cache write`() = runTest {
        val cache = BlockingProviderCacheService()
        val service = ProviderMediaHandleService(cache, nowSeconds = { 1_000L })
        val raw = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=2000000000"

        val first = async { service.createPath(raw) }
        cache.started.await()
        val second = async { service.createPath(raw) }
        cache.release.complete(Unit)

        assertEquals(first.await(), second.await())
        assertEquals(1, cache.setCalls.get())
    }

    @Test
    fun `media materialization only handles the selected provider`() = runTest {
        val service = ProviderMediaHandleService(FakeCacheService())
        val response = testStreamResponse(
            videoOnlyStreams = listOf(
                testVideoStream("https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=9999999999"),
            ),
        )

        val handled = service.materialize(response, ProviderMediaType.BILIBILI)
        assertTrue(handled.videoOnlyStreams.single().url.startsWith("/media/m1_"))
    }

    @Test
    fun `materialization covers optional stream media paths`() = runTest {
        val service = ProviderMediaHandleService(FakeCacheService())
        val video = testVideoStream(
            "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=9999999999",
        ).copy(
            manifestUrl = "https://upos-hz-mirrorakam.akamaized.net/video.m3u8?deadline=9999999999",
            sabrSessionUrl = "https://upos-hz-mirrorakam.akamaized.net/session?deadline=9999999999",
        )

        val handled = service.materialize(
            testStreamResponse(videoOnlyStreams = listOf(video)),
            ProviderMediaType.BILIBILI,
        )

        assertTrue(handled.videoOnlyStreams.single().manifestUrl?.startsWith("/media/m1_") == true)
        assertTrue(handled.videoOnlyStreams.single().sabrSessionUrl?.startsWith("/media/m1_") == true)
    }

    @Test
    fun `protocol relative provider URLs are materialized`() = runTest {
        val service = ProviderMediaHandleService(FakeCacheService())
        val response = testStreamResponse(
            videoOnlyStreams = listOf(
                testVideoStream("//upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=9999999999"),
            ),
        )

        val handled = service.materialize(response, ProviderMediaType.BILIBILI)

        assertTrue(handled.videoOnlyStreams.single().url.startsWith("/media/m1_"))
    }
}

private class RecordingProviderCacheService : CacheService {
    private val values = mutableMapOf<String, String>()
    var ttlSeconds: Long = 0

    override suspend fun set(key: String, value: String, ttlSeconds: Long) {
        this.ttlSeconds = ttlSeconds
        values[key] = value
    }

    override suspend fun get(key: String): String? = values[key]

    override suspend fun delete(key: String) { values.remove(key) }
}

private class BlockingProviderCacheService : CacheService {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val setCalls = AtomicInteger()
    private val values = ConcurrentHashMap<String, String>()

    override suspend fun set(key: String, value: String, ttlSeconds: Long) {
        setCalls.incrementAndGet()
        values[key] = value
        started.complete(Unit)
        release.await()
    }

    override suspend fun get(key: String): String? = values[key]

    override suspend fun delete(key: String) {
        values.remove(key)
    }
}
