package dev.typetype.server

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.ProviderSessionRenewal
import dev.typetype.server.services.StreamFailureMemory
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YoutubeStreamGuards
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSessionRenewalTest {
    private var time = 0L
    private val renewedFor = mutableListOf<String>()

    private fun renewal(scope: TestScope, enabled: Boolean = true) = ProviderSessionRenewal(
        enabled = enabled,
        refusals = 3,
        withinMs = 60_000,
        restMs = 120_000,
        now = { time },
        scope = scope,
    ) { url ->
        renewedFor += url
        true
    }

    @Test
    fun `three refusals within a minute renew the session once`() = runTest {
        val renewal = renewal(this)
        renewal.refused("a")
        renewal.refused("b")
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), renewedFor)
        time = 20_000
        renewal.refused("c")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("c"), renewedFor)
    }

    @Test
    fun `refusals far apart renew nothing`() = runTest {
        val renewal = renewal(this)
        listOf(0L, 50_000L, 100_000L, 150_000L).forEach { at ->
            time = at
            renewal.refused("v$at")
        }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), renewedFor)
    }

    @Test
    fun `a session just renewed is left alone for two minutes however many refusals follow`() = runTest {
        val renewal = renewal(this)
        repeat(3) { renewal.refused("first") }
        time = 30_000
        repeat(50) { renewal.refused("storm") }
        testScheduler.advanceUntilIdle()
        assertEquals(1, renewedFor.size)
        time = 121_000
        renewal.refused("later")
        renewal.refused("later")
        testScheduler.advanceUntilIdle()
        assertEquals(1, renewedFor.size)
        renewal.refused("later")
        testScheduler.advanceUntilIdle()
        assertEquals(2, renewedFor.size)
    }

    @Test
    fun `switched off it renews nothing`() = runTest {
        val renewal = renewal(this, enabled = false)
        repeat(10) { renewal.refused("v") }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), renewedFor)
    }

    @Test
    fun `only a refusal for being a machine counts, not a video that does not exist`() = runTest {
        var kind: ExtractionFailureKind? = ExtractionFailureKind.ContentUnavailable
        val provider = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
                kind?.let { ExtractionResult.Failure("no", "code", it) } ?: ExtractionResult.Failure("no")
        }
        val service = CachedStreamService(provider, FakeCacheService(), renewal = renewal(this))
        repeat(5) { service.getStreamInfo("https://youtu.be/gone$it") }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), renewedFor)
        kind = ExtractionFailureKind.ProviderAccessBlocked
        repeat(3) { service.getStreamInfo("https://youtu.be/refused$it") }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("https://youtu.be/refused2"), renewedFor)
    }

    @Test
    fun `YoutubeStreamGuards clears provider blocks and invokes onRenewed when recovery succeeds`() = runTest {
        val memory = StreamFailureMemory()
        memory.remember("stream:v6:https://youtu.be/gone", ExtractionResult.Failure("gone", "gone", ExtractionFailureKind.ContentUnavailable))
        var onRenewedCalls = 0
        val guards = YoutubeStreamGuards(
            recoverSession = { reason ->
                assertEquals("bot_check", reason)
                true
            },
            failures = memory,
            onRenewed = { onRenewedCalls += 1 },
            scope = this,
        )
        val provider = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
                ExtractionResult.Failure("bot", "bot_check", ExtractionFailureKind.ProviderAccessBlocked)
        }
        val cached = guards.cached(provider, FakeCacheService(), "stream:v6")

        repeat(3) { cached.getStreamInfo("https://youtu.be/blocked$it") }
        assertNotNull(memory.recall("stream:v6:https://youtu.be/blocked0"))

        testScheduler.advanceUntilIdle()

        assertEquals(1, onRenewedCalls)
        assertNull(memory.recall("stream:v6:https://youtu.be/blocked0"))
        assertNotNull(memory.recall("stream:v6:https://youtu.be/gone"))
    }

    @Test
    fun `YoutubeStreamGuards retains provider blocks when recovery fails`() = runTest {
        val memory = StreamFailureMemory()
        var onRenewedCalls = 0
        val guards = YoutubeStreamGuards(
            recoverSession = { false },
            failures = memory,
            onRenewed = { onRenewedCalls += 1 },
            scope = this,
        )
        val provider = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
                ExtractionResult.Failure("bot", "bot_check", ExtractionFailureKind.ProviderAccessBlocked)
        }
        val cached = guards.cached(provider, FakeCacheService(), "stream:v6")

        repeat(3) { cached.getStreamInfo("https://youtu.be/blocked$it") }
        testScheduler.advanceUntilIdle()

        assertEquals(0, onRenewedCalls)
        assertNotNull(memory.recall("stream:v6:https://youtu.be/blocked0"))
    }
}
