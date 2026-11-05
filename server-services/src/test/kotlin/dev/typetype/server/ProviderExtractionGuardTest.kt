package dev.typetype.server

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.ProviderExtractionGuard
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ProviderExtractionGuardTest {
    private val ok: ExtractionResult<StreamResponse> = ExtractionResult.Success(mockk(relaxed = true))
    private val riskControl: ExtractionResult<StreamResponse> =
        ExtractionResult.Failure("limited", "provider_rate_limited", ExtractionFailureKind.RateLimited, 60)
    private var clock = 1_000_000L

    private fun guard(maxConcurrent: Int = 3) = ProviderExtractionGuard(
        provider = "bilibili",
        maxConcurrent = maxConcurrent,
        firstPauseMs = 60_000,
        longestPauseMs = 240_000,
        now = { clock },
    )

    private fun ExtractionResult<StreamResponse>.isPaced() =
        this is ExtractionResult.Failure && code == CachedStreamService.PACED_CODE

    @Test
    fun `only a few videos are opened at the provider at once`() = runTest {
        val guard = guard(maxConcurrent = 2)
        val active = AtomicInteger()
        var peak = 0
        val results = (1..6).map {
            async {
                guard.run(nobodyWaits = false) {
                    peak = maxOf(peak, active.incrementAndGet())
                    delay(100)
                    active.decrementAndGet()
                    ok
                }
            }
        }.awaitAll()

        assertEquals(2, peak)
        assertTrue(results.all { it is ExtractionResult.Success })
    }

    @Test
    fun `a hover preview never waits for a busy provider`() = runTest {
        val guard = guard(maxConcurrent = 1)
        val release = CompletableDeferred<Unit>()
        val playing = launch { guard.run(nobodyWaits = false) { release.await(); ok } }
        yield()

        assertTrue(guard.run(nobodyWaits = true) { ok }.isPaced())
        release.complete(Unit)
        playing.join()
    }

    @Test
    fun `after a refusal one request at a time asks again and the rest wait it out`() = runTest {
        val guard = guard()
        guard.run(nobodyWaits = false) { riskControl }
        clock += 10_000

        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val probe = async { guard.run(nobodyWaits = false) { calls.incrementAndGet(); release.await(); ok } }
        yield()
        val other = guard.run(nobodyWaits = false) { calls.incrementAndGet(); ok }
        val preview = guard.run(nobodyWaits = true) { calls.incrementAndGet(); ok }

        assertTrue(other.isPaced())
        assertEquals(50L, (other as ExtractionResult.Failure).retryAfterSeconds)
        assertTrue(preview.isPaced())
        release.complete(Unit)
        assertTrue(probe.await() is ExtractionResult.Success)
        assertEquals(1, calls.get())
        assertTrue(guard.run(nobodyWaits = true) { ok } is ExtractionResult.Success)
    }

    @Test
    fun `the pause doubles at each refusal up to its longest`() = runTest {
        val guard = guard()
        val pauses = (1..4).map {
            guard.run(nobodyWaits = false) { riskControl }
            val left = guard.run(nobodyWaits = true) { ok } as ExtractionResult.Failure
            clock += 300_000
            left.retryAfterSeconds
        }

        assertEquals(listOf(60L, 120L, 240L, 240L), pauses)
    }
}
