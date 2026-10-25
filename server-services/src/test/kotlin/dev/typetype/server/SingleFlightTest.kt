package dev.typetype.server

import dev.typetype.server.services.SingleFlight
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightTest {
    @Test
    fun `concurrent callers share one load`() = runTest {
        val flights = SingleFlight<String>()
        val gate = CompletableDeferred<Unit>()
        val loads = AtomicInteger()
        val callers = List(5) {
            async {
                flights.run("key") {
                    loads.incrementAndGet()
                    gate.await()
                    "value"
                }
            }
        }
        yield()
        gate.complete(Unit)

        assertEquals(List(5) { "value" }, callers.map { it.await() })
        assertEquals(1, loads.get())
        assertEquals(0, flights.inFlight())
    }

    @Test
    fun `a cancelled leader does not fail the callers waiting on it`() = runTest {
        val flights = SingleFlight<String>()
        val leaderStarted = CompletableDeferred<Unit>()
        val loads = AtomicInteger()
        val leader = async {
            flights.run("key") {
                loads.incrementAndGet()
                leaderStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
                "never"
            }
        }
        leaderStarted.await()
        val waiter = async {
            flights.run("key") {
                loads.incrementAndGet()
                "recovered"
            }
        }
        yield()

        leader.cancelAndJoin()

        assertEquals("recovered", waiter.await())
        assertEquals(2, loads.get())
        assertEquals(0, flights.inFlight())
    }

    @Test
    fun `a failing load reaches every waiting caller`() = runTest {
        val flights = SingleFlight<String>()
        val gate = CompletableDeferred<Unit>()
        val callers = List(2) {
            async {
                runCatching {
                    flights.run("key") {
                        gate.await()
                        error("provider down")
                    }
                }
            }
        }
        yield()
        gate.complete(Unit)

        val outcomes = callers.map { it.await() }
        outcomes.forEach { outcome ->
            assertThrows(IllegalStateException::class.java) { outcome.getOrThrow() }
        }
    }
}
