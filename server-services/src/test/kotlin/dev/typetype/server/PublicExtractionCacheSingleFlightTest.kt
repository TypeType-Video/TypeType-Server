package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.PublicExtractionCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class PublicExtractionCacheSingleFlightTest {
    @Test
    fun `concurrent misses on one key ask the provider once`() = runTest {
        val cache = ExpiringFakeCache()
        val gate = CompletableDeferred<Unit>()
        val loads = AtomicInteger()
        val callers = List(4) {
            async {
                PublicExtractionCache.getOrLoad(cache, "test", "test:single-flight", String.serializer(), { 60L }) {
                    loads.incrementAndGet()
                    gate.await()
                    ExtractionResult.Success("value")
                }
            }
        }
        yield()
        gate.complete(Unit)

        assertEquals(List(4) { ExtractionResult.Success("value") }, callers.map { it.await() })
        assertEquals(1, loads.get())
    }
}
