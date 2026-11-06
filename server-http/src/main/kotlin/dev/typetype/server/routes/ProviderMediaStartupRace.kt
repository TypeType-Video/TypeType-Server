package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.services.ProviderMediaStartupCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select

/**
 * The warmup can stay stuck for seconds on a cold CDN connection while a fresh request answers at
 * once. A request still waiting for it after a short delay also asks upstream: the first answer
 * serves the player and the other one is dropped.
 */
internal suspend fun startupCacheOrUpstream(
    scope: CoroutineScope,
    cache: ProviderMediaStartupCache,
    url: String,
    rangeHeader: String?,
    hedgeAfterMillis: Long = STARTUP_HEDGE_AFTER_MILLIS,
    upstream: suspend () -> ExtractionResult<ProxyResponse>,
): ExtractionResult<ProxyResponse> {
    cache.respond(url, rangeHeader)?.let { return ExtractionResult.Success(it) }
    if (!cache.isPending(url)) return upstream()
    cache.awaitPending(url, hedgeAfterMillis)
    cache.respond(url, rangeHeader)?.let { return ExtractionResult.Success(it) }
    if (!cache.isPending(url)) return upstream()
    val relay = scope.async { upstream() }
    val warm = scope.async {
        cache.awaitPending(url)
        cache.respond(url, rangeHeader)
    }
    try {
        val warmFirst = select { warm.onAwait { true }; relay.onAwait { false } }
        val cached = if (warmFirst) warm.await() else null
        if (cached != null) {
            dropRelay(relay)
            return ExtractionResult.Success(cached)
        }
        warm.cancel()
        return relay.await()
    } catch (error: CancellationException) {
        warm.cancel()
        dropRelay(relay)
        throw error
    }
}

// Cancelling would lose an upstream answer still on its way, and with it the open connection.
@OptIn(ExperimentalCoroutinesApi::class)
private fun dropRelay(relay: Deferred<ExtractionResult<ProxyResponse>>) {
    relay.invokeOnCompletion { cause ->
        if (cause == null) (relay.getCompleted() as? ExtractionResult.Success)?.data?.close?.invoke()
    }
}

internal const val STARTUP_HEDGE_AFTER_MILLIS = 1_200L
