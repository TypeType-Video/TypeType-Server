package dev.typetype.server.services

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Bilibili and NicoNico block a server that opens too many new videos at once, and keep blocking
 * it while it insists. A few extractions run at a time; work nobody waits for (hover previews,
 * mirror samples) never queues. Once the provider refuses, only one request at a time asks it
 * again until it answers, the others fail at once with the time left, and the pause doubles at
 * each refusal.
 */
class ProviderExtractionGuard(
    private val provider: String,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    private val queueTimeoutMs: Long = DEFAULT_QUEUE_TIMEOUT_MS,
    private val firstPauseMs: Long = DEFAULT_FIRST_PAUSE_MS,
    private val longestPauseMs: Long = DEFAULT_LONGEST_PAUSE_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val permits = Semaphore(maxConcurrent.coerceAtLeast(1))
    private val probe = Mutex()

    @Volatile
    private var pausedUntil = 0L

    @Volatile
    private var pauseMs = 0L

    suspend fun run(
        nobodyWaits: Boolean,
        load: suspend () -> ExtractionResult<StreamResponse>,
    ): ExtractionResult<StreamResponse> {
        val left = pausedUntil - now()
        if (left > 0) {
            if (nobodyWaits || !probe.tryLock()) return refused(left)
            return try {
                record(load())
            } finally {
                probe.unlock()
            }
        }
        val admitted = if (nobodyWaits) permits.tryAcquire() else withTimeoutOrNull(queueTimeoutMs) { permits.acquire() } != null
        if (!admitted) return busy()
        return try {
            record(load())
        } finally {
            permits.release()
        }
    }

    private fun record(result: ExtractionResult<StreamResponse>): ExtractionResult<StreamResponse> {
        when {
            result.isRefusal() -> {
                pauseMs = if (pauseMs == 0L) firstPauseMs else (pauseMs * 2).coerceAtMost(longestPauseMs)
                pausedUntil = now() + pauseMs
                logger.warn("provider_guard event=paused provider={} pauseMs={}", provider, pauseMs)
            }
            result is ExtractionResult.Success && pauseMs != 0L -> {
                logger.info("provider_guard event=resumed provider={} lastPauseMs={}", provider, pauseMs)
                pauseMs = 0L
                pausedUntil = 0L
            }
        }
        return result
    }

    private fun ExtractionResult<StreamResponse>.isRefusal(): Boolean =
        this is ExtractionResult.Failure &&
            (kind == ExtractionFailureKind.RateLimited || kind == ExtractionFailureKind.ProviderAccessBlocked) &&
            code != CachedStreamService.PACED_CODE

    private fun refused(leftMs: Long): ExtractionResult.Failure = ExtractionResult.Failure(
        StreamExtractionErrorMapper.PROVIDER_RATE_LIMITED_FALLBACK,
        CachedStreamService.PACED_CODE,
        ExtractionFailureKind.RateLimited,
        (leftMs / 1000).coerceAtLeast(1),
    )

    private fun busy(): ExtractionResult.Failure = ExtractionResult.Failure(
        "Too many videos are being opened at the provider right now",
        CachedStreamService.PACED_CODE,
        ExtractionFailureKind.RateLimited,
        BUSY_RETRY_AFTER_SECONDS,
    )

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 3
        const val DEFAULT_QUEUE_TIMEOUT_MS = 20_000L
        const val DEFAULT_FIRST_PAUSE_MS = 60_000L
        const val DEFAULT_LONGEST_PAUSE_MS = 15 * 60_000L
        private const val BUSY_RETRY_AFTER_SECONDS = 5L
        private val logger = LoggerFactory.getLogger(ProviderExtractionGuard::class.java)
    }
}
