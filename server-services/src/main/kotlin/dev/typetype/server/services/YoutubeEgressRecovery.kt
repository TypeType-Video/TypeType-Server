package dev.typetype.server.services

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Lets public YouTube browsing (search, trending, channels, playlists) recover from an exit the
 * provider refuses, like playback already does: the refusal is counted by the egress rotation,
 * and when that moves to a new exit the YouTube caches and connections are reset and the request
 * that met the refusal is sent once more, so the viewer does not have to reload the page.
 *
 * Concurrent refusals share one recovery and wait at most [maxWaitMs] for it; the rotation's own
 * refusal count and rest period keep a burst of failing feed requests from rotating in a loop.
 */
class YoutubeEgressRecovery(
    private val rotate: suspend (reason: String) -> Boolean,
    private val reset: suspend (reason: String) -> Unit,
    private val maxWaitMs: Long = DEFAULT_MAX_WAIT_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private var running: Deferred<Boolean>? = null

    suspend fun <T> retryOnce(reason: String, load: suspend () -> ExtractionResult<T>): ExtractionResult<T> {
        val first = load()
        if (!first.isProviderRefusal() || !recover(reason)) return first
        val second = load()
        logger.info("youtube_egress event=public_retry reason={} recovered={}", reason, second is ExtractionResult.Success)
        return second
    }

    private suspend fun recover(reason: String): Boolean {
        val flight = join(reason)
        return withTimeoutOrNull(maxWaitMs) { flight.await() } ?: false
    }

    @Synchronized
    private fun join(reason: String): Deferred<Boolean> = running?.takeIf { it.isActive } ?: scope.async {
        val moved = runCatching { rotate(reason) }.getOrDefault(false)
        if (moved) runCatching { reset(reason) }
        moved
    }.also { running = it }

    private fun ExtractionResult<*>.isProviderRefusal(): Boolean =
        this is ExtractionResult.Failure &&
            (kind == ExtractionFailureKind.ProviderAccessBlocked || kind == ExtractionFailureKind.RateLimited) &&
            code != CachedStreamService.PACED_CODE

    companion object {
        const val DEFAULT_MAX_WAIT_MS = 25_000L
        private val logger = LoggerFactory.getLogger(YoutubeEgressRecovery::class.java)
    }
}

suspend fun <T> YoutubeEgressRecovery?.retrying(
    reason: String,
    load: suspend () -> ExtractionResult<T>,
): ExtractionResult<T> = this?.retryOnce(reason, load) ?: load()
