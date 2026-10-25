package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.StreamService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class CoalescedStreamCancellationTest {
    @Test
    fun `a waiter still gets the stream when the caller that started the load leaves`() = runTest {
        val response = testStreamResponse()
        val started = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val delegate = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                if (calls.incrementAndGet() == 1) {
                    started.complete(Unit)
                    awaitCancellation()
                }
                return ExtractionResult.Success(response)
            }
        }
        val service = CachedStreamService(delegate, FakeCacheService())

        val leader = launch { service.getStreamInfo(URL) }
        started.await()
        val waiter = async { service.getStreamInfo(URL) }
        yield()
        leader.cancel()

        assertEquals(ExtractionResult.Success(response), waiter.await())
        assertEquals(2, calls.get())
    }

    private companion object {
        const val URL = "https://www.youtube.com/watch?v=test"
    }
}
