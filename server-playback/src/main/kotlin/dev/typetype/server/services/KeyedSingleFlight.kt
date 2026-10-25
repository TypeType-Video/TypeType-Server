package dev.typetype.server.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.ContinuationInterceptor

class KeyedSingleFlight<K : Any, V>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val inFlight = ConcurrentHashMap<K, Deferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val callerContext = currentCoroutineContext().minusKey(Job).minusKey(ContinuationInterceptor)
        val created = scope.async(callerContext, start = CoroutineStart.LAZY) { block() }
        val existing = inFlight.putIfAbsent(key, created)
        if (existing != null) {
            created.cancel()
            return existing.await()
        }
        created.invokeOnCompletion { inFlight.remove(key, created) }
        created.start()
        return created.await()
    }
}
