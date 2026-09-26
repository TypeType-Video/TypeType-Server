package dev.typetype.server

import dev.typetype.server.services.withExtractionRetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException

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

    @Test
    fun `does not retry content availability failures`() = runBlocking {
        var calls = 0
        val error = runCatching {
            withExtractionRetry(attempts = 3, initialDelayMs = 1) {
                calls++
                throw ContentNotAvailableException("unavailable")
            }
        }.exceptionOrNull()

        assertEquals(ContentNotAvailableException::class.java, error?.javaClass)
        assertEquals(1, calls)
    }

    @Test
    fun `does not retry provider bot challenges`() = runBlocking {
        var calls = 0
        val error = runCatching {
            withExtractionRetry(attempts = 3, initialDelayMs = 1) {
                calls++
                throw AntiBotException("challenge")
            }
        }.exceptionOrNull()

        assertEquals(AntiBotException::class.java, error?.javaClass)
        assertEquals(1, calls)
    }

    @Test
    fun `propagates cancellation without retry`() = runBlocking {
        val cancelled = CancellationException("cancelled")
        var calls = 0
        val error = runCatching {
            withExtractionRetry(attempts = 3, initialDelayMs = 1) {
                calls++
                throw cancelled
            }
        }.exceptionOrNull()

        assertSame(cancelled, error)
        assertEquals(1, calls)
    }

    @Test
    fun `does not retry access restriction failures`() = runBlocking {
        val failures = listOf<Throwable>(
            org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException("age restricted"),
            org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException("geographic restriction"),
            org.schabi.newpipe.extractor.exceptions.PaidContentException("paid content"),
            org.schabi.newpipe.extractor.exceptions.PrivateContentException("private content"),
        )

        failures.forEach { failure ->
            var calls = 0
            val error = runCatching {
                withExtractionRetry(attempts = 3, initialDelayMs = 1) {
                    calls++
                    throw failure
                }
            }.exceptionOrNull()

            assertSame(failure, error)
            assertEquals(1, calls)
        }
    }
}
