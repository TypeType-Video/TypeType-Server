package dev.typetype.server.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class SharedExclusiveGate(
    private val capacity: Int,
    private val maxWaitMs: Long = Long.MAX_VALUE,
    private val timeoutMessage: String = "Timed out waiting for extraction permits",
) {
    private val exclusiveTurn = Mutex()
    private val permits = Semaphore(capacity)

    suspend fun <T> shared(block: suspend () -> T): T {
        var acquired = false
        try {
            awaitWithinLimit { exclusiveTurn.withLock { permits.acquire(); acquired = true } }
            return block()
        } finally {
            if (acquired) permits.release()
        }
    }

    suspend fun <T> exclusive(block: suspend () -> T): T {
        var locked = false
        var held = 0
        try {
            awaitWithinLimit {
                exclusiveTurn.lock()
                locked = true
                while (held < capacity) {
                    permits.acquire()
                    held++
                }
            }
            return block()
        } finally {
            repeat(held) { permits.release() }
            if (locked) exclusiveTurn.unlock()
        }
    }

    private suspend fun awaitWithinLimit(acquire: suspend () -> Unit) {
        if (maxWaitMs == Long.MAX_VALUE) return acquire()
        withTimeoutOrNull(maxWaitMs) { acquire() } ?: error(timeoutMessage)
    }
}
