package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProviderEgressBreakerTest {
    private var now = 1_000_000L
    private val breaker = ProviderEgressBreaker(now = { now })

    @Test
    fun `three refusals within a minute open the breaker`() {
        repeat(3) { breaker.recordRefusal(); now += 10_000L }

        assertEquals(20_000L, breaker.admit())
        assertEquals(20_000L, breaker.openRetryAfterMs())
    }

    @Test
    fun `refusals spread over more than a minute keep it closed`() {
        repeat(3) { breaker.recordRefusal(); now += 40_000L }

        assertNull(breaker.admit())
    }

    @Test
    fun `after the cooldown a single probe is admitted`() {
        repeat(3) { breaker.recordRefusal() }
        now += 30_000L

        assertNull(breaker.admit())
        assertEquals(30_000L, breaker.admit())
        assertNull(breaker.openRetryAfterMs())
    }

    @Test
    fun `a successful probe closes the breaker`() {
        repeat(3) { breaker.recordRefusal() }
        now += 30_000L
        breaker.admit()

        breaker.recordSuccess()

        assertNull(breaker.admit())
        assertNull(breaker.admit())
    }

    @Test
    fun `a refused probe reopens with a longer cooldown`() {
        repeat(3) { breaker.recordRefusal() }
        now += 30_000L
        breaker.admit()

        breaker.recordRefusal()

        assertEquals(60_000L, breaker.admit())
    }

    @Test
    fun `a probe that never reports frees the slot after its timeout`() {
        repeat(3) { breaker.recordRefusal() }
        now += 30_000L
        breaker.admit()
        now += 30_000L

        assertNull(breaker.admit())
    }
}
