package dev.typetype.server

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.StreamExtractionErrorMapper
import dev.typetype.server.services.runExtractionCatching
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtractionCatchingTest {
    @Test
    fun `turns an inner extraction timeout into an upstream timeout failure`() = runBlocking {
        val result = runExtractionCatching { withTimeout(10L) { delay(5_000L) } }

        val error = result.exceptionOrNull()
        assertTrue(error is TimeoutCancellationException)
        val mapped = StreamExtractionErrorMapper.map<Any>(checkNotNull(error))
        assertEquals(ExtractionFailureKind.UpstreamTimeout, (mapped as ExtractionResult.Failure).kind)
    }

    @Test
    fun `rethrows cancellation of the caller`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var continuedAfterCancel = false
        val job = launch {
            runExtractionCatching {
                started.complete(Unit)
                awaitCancellation()
            }
            continuedAfterCancel = true
        }
        started.await()
        job.cancel()
        job.join()

        assertFalse(continuedAfterCancel)
    }

    @Test
    fun `keeps ordinary failures as results`() = runBlocking {
        val result = runExtractionCatching { error("boom") }

        assertEquals("boom", result.exceptionOrNull()?.message)
    }
}
