package dev.typetype.server

import dev.typetype.server.services.withExtractionRetry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtractionResilienceTest {
    @Test
    fun `returns result without retries when block succeeds`() = runBlocking {
        var calls = 0
        val result = withExtractionRetry(attempts = 3, initialDelayMs = 1) {
            calls += 1
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries retriable error then succeeds`() = runBlocking {
        var calls = 0
        val result = withExtractionRetry(attempts = 3, initialDelayMs = 1) {
            calls += 1
            if (calls < 3) throw RuntimeException("temporary")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `does not retry illegal argument exception`() = runBlocking {
        var calls = 0
        val error = runCatching {
            withExtractionRetry(attempts = 3, initialDelayMs = 1) {
                calls += 1
                throw IllegalArgumentException("bad input")
            }
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertEquals(1, calls)
    }
}
