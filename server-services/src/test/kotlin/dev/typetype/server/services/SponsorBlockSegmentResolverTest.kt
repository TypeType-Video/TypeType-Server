package dev.typetype.server.services

import dev.typetype.server.FakeCacheService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import java.io.IOException

class SponsorBlockSegmentResolverTest {
    private val cache = FakeCacheService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clock = 0L
    private var fetches = 0
    private val resolver = SponsorBlockSegmentResolver(
        cache, scope, waitMs = 50L, fetchTimeoutMs = 5_000L, cooldownMs = 1_000L, now = { clock },
    )

    private fun segment() = SponsorBlockSegment("", 1.0, 5.0, SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP, 0)

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `caches fetched segments and serves them without a second fetch`() = runBlocking {
        repeat(2) {
            val resolution = resolver.resolve("video") { fetches++; arrayOf(segment()) }
            assertEquals(1, resolution.segments.size)
            assertTrue(resolution.available)
        }
        assertEquals(1, fetches)
    }

    @Test
    fun `a slow upstream stops delaying playback and still fills the cache in the background`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val first = resolver.resolve("video") {
            release.await()
            arrayOf(segment())
        }
        assertFalse(first.available)
        assertEquals(0, first.segments.size)
        assertNull(cache.get("sponsorblock:video"))
        assertFalse(resolver.resolve("other") { fetches++; arrayOf(segment()) }.available)
        assertEquals(0, fetches)

        release.complete(Unit)
        withTimeout(2_000L) { while (cache.get("sponsorblock:video") == null) delay(5L) }
        val second = resolver.resolve("video") { fetches++; emptyArray() }
        assertTrue(second.available)
        assertEquals(1, second.segments.size)
        assertEquals(0, fetches)
        assertTrue(resolver.resolve("other") { fetches++; arrayOf(segment()) }.available)
        assertEquals(1, fetches)
    }

    @Test
    fun `a failure is never cached and pauses the upstream for the cooldown`() = runBlocking {
        val failed = resolver.resolve("first") { fetches++; throw IOException("unreachable") }
        assertFalse(failed.available)
        assertNull(cache.get("sponsorblock:first"))

        assertFalse(resolver.resolve("second") { fetches++; arrayOf(segment()) }.available)
        assertEquals(1, fetches)

        clock = 1_000L
        val recovered = resolver.resolve("second") { fetches++; arrayOf(segment()) }
        assertEquals(2, fetches)
        assertTrue(recovered.available)
        assertEquals(1, recovered.segments.size)
    }
}
