package dev.typetype.server

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.StreamExtractionErrorMapper
import dev.typetype.server.services.StreamFailureMemory
import dev.typetype.server.services.StreamService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class StreamFailureMemoryTest {
    private var now = 0L

    @Test
    fun `deterministic failures are answered from memory within the ttl`() = runBlocking {
        val blocked = ExtractionResult.Failure("blocked", "provider_access_blocked", ExtractionFailureKind.ProviderAccessBlocked)
        val delegate = CountingStreamService(blocked)
        val service = service(delegate)

        assertEquals(blocked, service.getStreamInfo(URL))
        assertEquals(blocked, service.getStreamInfo(URL))
        assertEquals(1, delegate.calls.get())

        now += 20_001L
        service.getStreamInfo(URL)
        assertEquals(2, delegate.calls.get())
    }

    @Test
    fun `content restrictions and missing content are remembered`() = runBlocking {
        listOf(
            ExtractionResult.BadRequest("Only in Japan", "geographic_restriction"),
            ExtractionResult.BadRequest("Sign in", "age_restricted"),
            ExtractionResult.Failure("gone", "content_unavailable", ExtractionFailureKind.ContentUnavailable),
            StreamExtractionErrorMapper.map(IllegalArgumentException("unsupported url")),
        ).forEach { result ->
            val delegate = CountingStreamService(result)
            val service = service(delegate)
            service.getStreamInfo(URL)
            service.getStreamInfo(URL)
            assertEquals(1, delegate.calls.get(), result.toString())
        }
    }

    @Test
    fun `transient failures are asked again`() = runBlocking {
        listOf(
            ExtractionResult.Failure("boom", "upstream_failure"),
            ExtractionResult.Failure("late", "upstream_timeout", ExtractionFailureKind.UpstreamTimeout),
            ExtractionResult.Failure("slow", "provider_rate_limited", ExtractionFailureKind.RateLimited),
        ).forEach { result ->
            val delegate = CountingStreamService(result)
            val service = service(delegate)
            service.getStreamInfo(URL)
            service.getStreamInfo(URL)
            assertEquals(2, delegate.calls.get(), result.toString())
        }
    }

    @Test
    fun `memory stays bounded`() {
        val memory = StreamFailureMemory(ttlMs = 1_000L, maxEntries = 2, nowMs = { now })
        val missing = ExtractionResult.Failure("gone", "content_unavailable", ExtractionFailureKind.ContentUnavailable)
        memory.remember("a", missing)
        memory.remember("b", missing)
        memory.remember("c", missing)

        assertEquals(null, memory.recall("c"))
        now += 1_001L
        memory.remember("c", missing)
        assertEquals(missing, memory.recall("c"))
    }

    private fun service(delegate: StreamService): CachedStreamService =
        CachedStreamService(delegate, FakeCacheService(), failures = StreamFailureMemory(nowMs = { now }))

    private class CountingStreamService(private val result: ExtractionResult<StreamResponse>) : StreamService {
        val calls = AtomicInteger()

        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
            calls.incrementAndGet()
            return result
        }
    }

    private companion object {
        const val URL = "https://www.youtube.com/watch?v=pXo2wC0lfo0"
    }
}
