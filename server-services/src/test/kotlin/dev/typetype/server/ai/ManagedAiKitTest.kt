package dev.typetype.server.ai

import dev.typetype.server.FakeCacheService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ManagedAiKitTest {
    @Test
    fun `returns disabled or failed on blank prompt without hitting provider`() = runTest {
        val disabled = ManagedAiKit(provider = null, cache = FakeCacheService(), rpmLimit = 10, dailyLimit = 100)
        assertFalse(disabled.enabled)
        assertEquals(AiCompletionResult.Disabled, disabled.complete(AiCompletionRequest("hi")))

        val calls = AtomicInteger(0)
        val provider = stubProvider(calls = calls) { AiCompletionResult.Success("ok", "m1", "gemini") }
        val active = ManagedAiKit(provider = provider, cache = FakeCacheService(), rpmLimit = 10, dailyLimit = 100)
        assertTrue(active.enabled)
        assertEquals(
            AiCompletionResult.Failed("prompt_blank", retryable = false),
            active.complete(AiCompletionRequest("   ")),
        )
        assertEquals(0, calls.get())
    }

    @Test
    fun `caches successful responses per provider and model without caching failures`() = runTest {
        val cache = FakeCacheService()
        val calls = AtomicInteger(0)
        val modelA = stubProvider(model = "model-a", calls = calls) {
            AiCompletionResult.Success("ans-${calls.get()}", "model-a", "gemini")
        }
        val kitA = ManagedAiKit(provider = modelA, cache = cache, rpmLimit = 0, dailyLimit = 0)

        val first = kitA.complete(AiCompletionRequest("q1", cacheKey = "same-key"))
        val second = kitA.complete(AiCompletionRequest("q1", cacheKey = "same-key"))
        assertEquals(AiCompletionResult.Success("ans-1", "model-a", "gemini", cached = false), first)
        assertEquals(AiCompletionResult.Success("ans-1", "model-a", "gemini", cached = true), second)
        assertEquals(1, calls.get())

        val modelB = stubProvider(model = "model-b", calls = calls) {
            AiCompletionResult.Success("ans-b", "model-b", "gemini")
        }
        val kitB = ManagedAiKit(provider = modelB, cache = cache, rpmLimit = 0, dailyLimit = 0)
        val third = kitB.complete(AiCompletionRequest("q1", cacheKey = "same-key"))
        assertEquals(AiCompletionResult.Success("ans-b", "model-b", "gemini", cached = false), third)
        assertEquals(2, calls.get())

        val failCalls = AtomicInteger(0)
        val failing = stubProvider(calls = failCalls) {
            AiCompletionResult.Failed("upstream_unavailable", retryable = true)
        }
        val failKit = ManagedAiKit(provider = failing, cache = FakeCacheService(), rpmLimit = 0, dailyLimit = 0)
        failKit.complete(AiCompletionRequest("q1", cacheKey = "fail-key"))
        failKit.complete(AiCompletionRequest("q1", cacheKey = "fail-key"))
        assertEquals(2, failCalls.get())
    }

    @Test
    fun `enforces rpm and daily rate limits using cache`() = runTest {
        var clockMs = 1_700_000_000_000L
        val rpmCache = FakeCacheService()
        val calls = AtomicInteger(0)
        val provider = stubProvider(calls = calls) { AiCompletionResult.Success("ok", "m", "gemini") }

        val rpmKit = ManagedAiKit(provider, rpmCache, rpmLimit = 2, dailyLimit = 0) { clockMs }
        assertTrue(rpmKit.complete(AiCompletionRequest("1")) is AiCompletionResult.Success)
        assertTrue(rpmKit.complete(AiCompletionRequest("2")) is AiCompletionResult.Success)
        assertEquals(
            AiCompletionResult.RateLimited("local_rpm_limit_exceeded"),
            rpmKit.complete(AiCompletionRequest("3")),
        )
        assertEquals(2, calls.get())

        clockMs += 60_000L
        assertTrue(rpmKit.complete(AiCompletionRequest("4")) is AiCompletionResult.Success)

        val dailyKit = ManagedAiKit(provider, FakeCacheService(), rpmLimit = 0, dailyLimit = 1) { clockMs }
        assertTrue(dailyKit.complete(AiCompletionRequest("d1")) is AiCompletionResult.Success)
        assertEquals(
            AiCompletionResult.RateLimited("local_daily_limit_exceeded"),
            dailyKit.complete(AiCompletionRequest("d2")),
        )
    }

    private fun stubProvider(
        id: String = "gemini",
        model: String = "m1",
        calls: AtomicInteger,
        handler: suspend (AiCompletionRequest) -> AiCompletionResult,
    ): AiProvider = object : AiProvider {
        override val id: String = id
        override val model: String = model

        override suspend fun complete(request: AiCompletionRequest): AiCompletionResult {
            calls.incrementAndGet()
            return handler(request)
        }
    }
}
