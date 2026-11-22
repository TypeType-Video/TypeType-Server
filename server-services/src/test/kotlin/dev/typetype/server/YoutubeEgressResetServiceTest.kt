package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.StreamFailureMemory
import dev.typetype.server.services.YoutubeEgressResetResponse
import dev.typetype.server.services.YoutubeEgressResetResult
import dev.typetype.server.services.YoutubeEgressResetService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeEgressResetServiceTest {
    @Test
    fun `reset clears backoff keys, failure blocks, connections, and triggers session recovery`() = runTest {
        val cache = FakeCacheService()
        cache.set("youtube-related:v1:dQw4w9WgXcQ:backoff", "{}", 60)
        cache.set("youtube-related:v1:jNQXAC9IVRw:backoff", "{}", 60)
        cache.set("youtube-related:v1:dQw4w9WgXcQ", "cached", 60)
        cache.set("channel:v2:abc:backoff", "{}", 60)
        cache.set("comments-media:v2:def:backoff", "{}", 60)
        cache.set("search-v4:v2:ghi:backoff", "{}", 60)
        cache.set("channel-bilibili-v3:v2:bili:backoff", "{}", 60)

        val memory = StreamFailureMemory()
        memory.remember("stream:v1:yt", ExtractionResult.Failure("bot", "bot_check", ExtractionFailureKind.ProviderAccessBlocked))
        memory.remember("stream:v1:gone", ExtractionResult.Failure("gone", "gone", ExtractionFailureKind.ContentUnavailable))

        var evicted = 0
        var recoveredReason: String? = null
        val service = YoutubeEgressResetService(
            cache = cache,
            clearFailureMemory = memory::clearProviderBlocks,
            evictConnections = { evicted += 1 },
            recoverTokenSession = { reason ->
                recoveredReason = reason
                true
            },
        )

        val result = service.reset("  rotation_complete  ")

        assertEquals(
            YoutubeEgressResetResult.Completed(
                YoutubeEgressResetResponse(
                    clearedFailureEntries = 1,
                    clearedBackoffKeys = 6,
                    tokenSessionRecoveryTriggered = true,
                ),
            ),
            result,
        )
        assertEquals(1, evicted)
        assertEquals("rotation_complete", recoveredReason)
        assertNull(memory.recall("stream:v1:yt"))
        assertNotNull(memory.recall("stream:v1:gone"))
        assertNull(cache.get("youtube-related:v1:dQw4w9WgXcQ:backoff"))
        assertNull(cache.get("youtube-related:v1:jNQXAC9IVRw:backoff"))
        assertNotNull(cache.get("youtube-related:v1:dQw4w9WgXcQ"))
        assertNull(cache.get("channel:v2:abc:backoff"))
        assertNull(cache.get("comments-media:v2:def:backoff"))
        assertNull(cache.get("search-v4:v2:ghi:backoff"))
        assertNull(cache.get("channel-bilibili-v3:v2:bili:backoff"))
    }

    @Test
    fun `reset enforces cooldown and skips side effects while throttled`() = runTest {
        var now = 1_000L
        var clearedCalls = 0
        var evicted = 0
        var recoverCalls = 0
        val service = YoutubeEgressResetService(
            cache = FakeCacheService(),
            clearFailureMemory = {
                clearedCalls += 1
                0
            },
            evictConnections = { evicted += 1 },
            recoverTokenSession = {
                recoverCalls += 1
                true
            },
            restMs = 120_000L,
            nowMs = { now },
        )

        assertTrue(service.reset("first") is YoutubeEgressResetResult.Completed)
        now += 30_000L
        assertEquals(YoutubeEgressResetResult.Throttled(90L), service.reset("burst"))
        assertEquals(1, clearedCalls)
        assertEquals(1, evicted)
        assertEquals(1, recoverCalls)

        now += 90_000L
        assertTrue(service.reset("after_cooldown") is YoutubeEgressResetResult.Completed)
        assertEquals(2, clearedCalls)
        assertEquals(2, evicted)
        assertEquals(2, recoverCalls)
    }

    @Test
    fun `reset continues when cache deleteMatching throws`() = runTest {
        val failingCache = object : CacheService {
            override suspend fun get(key: String): String? = null
            override suspend fun set(key: String, value: String, ttlSeconds: Long) {}
            override suspend fun delete(key: String) {}
            override suspend fun deleteMatching(pattern: String): Int = error("cache unavailable")
        }
        var evicted = 0
        var recoveryCalled = false
        val service = YoutubeEgressResetService(
            cache = failingCache,
            clearFailureMemory = { 2 },
            evictConnections = { evicted += 1 },
            recoverTokenSession = {
                recoveryCalled = true
                false
            },
        )

        val result = service.reset("")

        assertEquals(YoutubeEgressResetResult.Completed(YoutubeEgressResetResponse(2, 0, false)), result)
        assertEquals(1, evicted)
        assertTrue(recoveryCalled)
    }

    @Test
    fun `isAuthorized fails closed when unconfigured or mismatched`() {
        val unconfigured = YoutubeEgressResetService(FakeCacheService(), { 0 }, {}, { false }, tokenProvider = { null })
        assertFalse(unconfigured.isAuthorized(null))
        assertFalse(unconfigured.isAuthorized("secret"))

        val configured = YoutubeEgressResetService(FakeCacheService(), { 0 }, {}, { false }, tokenProvider = { "secret-token" })
        assertFalse(configured.isAuthorized(null))
        assertFalse(configured.isAuthorized("   "))
        assertFalse(configured.isAuthorized("wrong-token"))
        assertTrue(configured.isAuthorized("secret-token"))
        assertTrue(configured.isAuthorized(" secret-token "))
    }
}
