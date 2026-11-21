package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A bot check is handed to the same recovery as a session kept on hold, so one thing at a time
 * replaces the token session or moves the egress: two of them at once judge exits with each
 * other's sessions.
 */
class YoutubeStreamGuards(
    private val recoverSession: suspend (reason: String) -> Boolean,
    val failures: StreamFailureMemory = StreamFailureMemory(),
    private val onRenewed: (() -> Unit)? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val watch = StreamRequestWatch()
    private val pace = StreamOpenPace()
    private val renewal = ProviderSessionRenewal(scope = scope) { _ ->
        val renewed = recoverSession("bot_check")
        if (renewed) {
            failures.clearProviderBlocks()
            onRenewed?.invoke()
        }
        renewed
    }

    fun cached(delegate: StreamService, cache: CacheService, cachePrefix: String): CachedStreamService =
        CachedStreamService(delegate, cache, cachePrefix, watch, pace, renewal, failures)
}
