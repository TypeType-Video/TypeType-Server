package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

class SingleFlight<V> {
    private val calls = ConcurrentHashMap<String, CompletableDeferred<V>>()

    suspend fun run(key: String, block: suspend () -> V): V {
        while (true) {
            val pending = CompletableDeferred<V>()
            val existing = calls.putIfAbsent(key, pending) ?: return lead(key, pending, block)
            try {
                return existing.await()
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    fun inFlight(): Int = calls.size

    private suspend fun lead(key: String, pending: CompletableDeferred<V>, block: suspend () -> V): V = try {
        block().also { pending.complete(it) }
    } catch (error: Throwable) {
        pending.completeExceptionally(error)
        throw error
    } finally {
        calls.remove(key, pending)
    }
}
