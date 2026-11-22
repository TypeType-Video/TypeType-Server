package dev.typetype.server

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.YoutubeEgressRecovery
import dev.typetype.server.services.retrying
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class YoutubeEgressRecoveryTest {
    private val blocked = ExtractionResult.Failure("bot", "provider_access_blocked", ExtractionFailureKind.ProviderAccessBlocked)

    @Test
    fun `a refused request is sent once more after the exit moved`() = runBlocking {
        val resets = mutableListOf<String>()
        val recovery = YoutubeEgressRecovery(rotate = { true }, reset = { resets += it })
        val calls = AtomicInteger()

        val result = recovery.retryOnce("search") {
            if (calls.incrementAndGet() == 1) blocked else ExtractionResult.Success("page")
        }

        assertEquals(ExtractionResult.Success("page"), result)
        assertEquals(2, calls.get())
        assertEquals(listOf("search"), resets)
    }

    @Test
    fun `no retry when the exit did not move`() = runBlocking {
        val recovery = YoutubeEgressRecovery(rotate = { false }, reset = { error("no reset") })
        val calls = AtomicInteger()

        assertEquals(blocked, recovery.retryOnce("search") { calls.incrementAndGet(); blocked })
        assertEquals(1, calls.get())
    }

    @Test
    fun `local pacing and ordinary failures never rotate`() = runBlocking {
        val rotations = AtomicInteger()
        val recovery = YoutubeEgressRecovery(rotate = { rotations.incrementAndGet(); true }, reset = {})
        val paced = ExtractionResult.Failure("busy", CachedStreamService.PACED_CODE, ExtractionFailureKind.RateLimited)
        val gone = ExtractionResult.Failure("gone", "gone", ExtractionFailureKind.ContentUnavailable)

        recovery.retryOnce("search") { paced }
        recovery.retryOnce("channel") { gone }

        assertEquals(0, rotations.get())
    }

    @Test
    fun `concurrent refusals share one recovery`() = runBlocking {
        val rotations = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val recovery = YoutubeEgressRecovery(rotate = { rotations.incrementAndGet(); release.await(); true }, reset = {})
        val attempts = AtomicInteger()

        val results = (1..5).map {
            async {
                var first = true
                recovery.retryOnce("feed") {
                    attempts.incrementAndGet()
                    if (first) { first = false; blocked } else ExtractionResult.Success("ok")
                }
            }
        }
        repeat(10) { yield() }
        release.complete(Unit)

        assertEquals(List(5) { ExtractionResult.Success("ok") }, results.awaitAll())
        assertEquals(1, rotations.get())
        assertEquals(10, attempts.get())
    }

    @Test
    fun `a slow rotation gives the refusal back after the wait`() = runBlocking {
        val recovery = YoutubeEgressRecovery(rotate = { CompletableDeferred<Unit>().await(); true }, reset = {}, maxWaitMs = 50L)

        assertEquals(blocked, recovery.retryOnce("search") { blocked })
    }

    @Test
    fun `without recovery the request runs once`() = runBlocking {
        val recovery: YoutubeEgressRecovery? = null
        val calls = AtomicInteger()

        assertEquals(blocked, recovery.retrying("search") { calls.incrementAndGet(); blocked })
        assertEquals(1, calls.get())
    }
}
