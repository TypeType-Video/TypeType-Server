package dev.typetype.server.services

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SharedExclusiveGateTest {
    @Test
    fun `suspended shared holders and a waiting exclusive finish on two threads`() = runBlocking {
        val gate = SharedExclusiveGate(capacity = 64)
        val twoThreads = Dispatchers.IO.limitedParallelism(2)
        val finished = AtomicInteger()

        withTimeout(10_000) {
            withContext(twoThreads) {
                val shared = List(200) {
                    async { gate.shared { delay(20); finished.incrementAndGet() } }
                }
                val exclusive = List(3) {
                    async { gate.exclusive { delay(5); finished.incrementAndGet() } }
                }
                (shared + exclusive).awaitAll()
            }
        }

        assertEquals(203, finished.get())
    }

    @Test
    fun `exclusive runs alone and new shared work waits behind it`() = runBlocking {
        val gate = SharedExclusiveGate(capacity = 4)
        val sharedRunning = CompletableDeferred<Unit>()
        val releaseShared = CompletableDeferred<Unit>()
        val exclusiveRan = CompletableDeferred<Unit>()
        var lateSharedSawExclusive = false

        val first = launch { gate.shared { sharedRunning.complete(Unit); releaseShared.await() } }
        sharedRunning.await()
        val exclusive = launch { gate.exclusive { exclusiveRan.complete(Unit) } }
        yield()
        val late = launch { gate.shared { lateSharedSawExclusive = exclusiveRan.isCompleted } }
        yield()

        assertFalse(exclusiveRan.isCompleted)
        releaseShared.complete(Unit)
        listOf(first, exclusive, late).forEach { it.join() }

        assertTrue(lateSharedSawExclusive)
    }

    @Test
    fun `a bounded wait reports a timeout and gives every permit back`() = runBlocking {
        val gate = SharedExclusiveGate(capacity = 2, maxWaitMs = 50, timeoutMessage = "busy")
        val release = CompletableDeferred<Unit>()
        val holder = launch { gate.shared { release.await() } }
        yield()

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { gate.exclusive { } }
        }
        release.complete(Unit)
        holder.join()

        assertEquals("busy", failure.message)
        assertEquals(1, gate.exclusive { 1 })
        assertEquals(2, gate.shared { 2 })
    }

    @Test
    fun `a cancelled exclusive wait leaves the gate usable`() = runBlocking {
        val gate = SharedExclusiveGate(capacity = 2)
        val release = CompletableDeferred<Unit>()
        val holder = launch { gate.shared { release.await() } }
        yield()
        val waiting = launch { gate.exclusive { } }
        yield()

        waiting.cancel()
        waiting.join()
        release.complete(Unit)
        holder.join()

        assertEquals(3, withTimeout(1_000) { gate.exclusive { 3 } })
    }
}
