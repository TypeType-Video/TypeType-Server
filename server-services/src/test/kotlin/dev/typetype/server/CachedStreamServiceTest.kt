package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.StreamService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class CachedStreamServiceTest {

    @Test
    fun `concurrent cache misses share one delegate extraction`() = runBlocking {
        val response = testStreamResponse()
        val delegate = CountingStreamService(ExtractionResult.Success(response))
        val cache = RecordingCacheService()
        val service = CachedStreamService(delegate, cache)
        val results = concurrentRequests(service, REQUEST_URL)
        assertEquals(List(REQUEST_COUNT) { ExtractionResult.Success(response) }, results)
        assertEquals(1, delegate.calls.get())
        assertEquals(1, cache.setCalls.get())
    }

    @Test
    fun `cached stream skips delegate after first fill`() = runBlocking {
        val response = testStreamResponse()
        val delegate = CountingStreamService(ExtractionResult.Success(response))
        val service = CachedStreamService(delegate, RecordingCacheService())
        assertEquals(ExtractionResult.Success(response), service.getStreamInfo(REQUEST_URL))
        assertEquals(ExtractionResult.Success(response), service.getStreamInfo(REQUEST_URL))
        assertEquals(1, delegate.calls.get())
    }

    @Test
    fun `stream cache key is versioned`() {
        assertEquals("stream:v6:$REQUEST_URL", CachedStreamService.cacheKey(REQUEST_URL))
    }

    @Test
    fun `concurrent delegate failures are shared and not cached`() = runBlocking {
        val delegate = CountingStreamService(ExtractionResult.Failure("blocked"))
        val cache = RecordingCacheService()
        val service = CachedStreamService(delegate, cache)
        val results = concurrentRequests(service, REQUEST_URL)
        assertEquals(List(REQUEST_COUNT) { ExtractionResult.Failure("blocked") }, results)
        assertEquals(1, delegate.calls.get())
        assertEquals(0, cache.setCalls.get())
    }

    private suspend fun concurrentRequests(
        service: CachedStreamService,
        url: String,
    ): List<ExtractionResult<StreamResponse>> = coroutineScope {
        List(REQUEST_COUNT) { async { service.getStreamInfo(url) } }.awaitAll()
    }

    private class CountingStreamService(
        private val result: ExtractionResult<StreamResponse>,
    ) : StreamService {
        val calls = AtomicInteger()

        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
            calls.incrementAndGet()
            delay(50)
            return result
        }
    }

    private class RecordingCacheService : CacheService {
        private val values = mutableMapOf<String, String>()
        val setCalls = AtomicInteger()

        override suspend fun get(key: String): String? = values[key]

        override suspend fun set(key: String, value: String, ttlSeconds: Long) {
            setCalls.incrementAndGet()
            values[key] = value
        }

        override suspend fun delete(key: String) {
            values.remove(key)
        }
    }

    private companion object {
        const val REQUEST_COUNT = 8
        const val REQUEST_URL = "https://www.youtube.com/watch?v=test"
    }
}
