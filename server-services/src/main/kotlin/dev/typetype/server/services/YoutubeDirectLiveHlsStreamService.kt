package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

class YoutubeDirectLiveHlsStreamService(
    private val liveHlsService: StreamService,
    private val fallbackService: StreamService? = null,
) : StreamService {
    private val logger = LoggerFactory.getLogger(YoutubeDirectLiveHlsStreamService::class.java)

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = coroutineScope {
        if (!isYoutubeUrl(url)) return@coroutineScope ExtractionResult.BadRequest("Invalid YouTube URL")

        val videoId = youtubeVideoId(url) ?: "unknown"
        val totalStartedAt = System.nanoTime()
        val directStartedAt = System.nanoTime()
        val directAttempt = async { timedExtraction(liveHlsService, url, directStartedAt) }
        val directBeforeHedge = withTimeoutOrNull(HEDGE_DELAY_MILLIS) { directAttempt.await() }

        if (directBeforeHedge != null) {
            return@coroutineScope resolveDirect(
                url = url,
                videoId = videoId,
                direct = directBeforeHedge,
                totalStartedAt = totalStartedAt,
            )
        }

        val fallback = fallbackService
        if (fallback == null) {
            return@coroutineScope resolveDirect(
                url = url,
                videoId = videoId,
                direct = directAttempt.await(),
                totalStartedAt = totalStartedAt,
            )
        }

        val fallbackStartedAt = System.nanoTime()
        val fallbackAttempt = async { timedExtraction(fallback, url, fallbackStartedAt) }
        try {
            val first = select {
                directAttempt.onAwait { CompletedAttempt(AttemptSource.DIRECT, it) }
                fallbackAttempt.onAwait { CompletedAttempt(AttemptSource.FALLBACK, it) }
            }
            if (first.source == AttemptSource.FALLBACK && first.extraction.result.isLiveHls()) {
                val totalElapsedMs = elapsedMsSince(totalStartedAt)
                logger.info(
                    "youtube_live_resolution videoId={} directMs={} fallbackMs={} totalMs={} directCode=pending result=live_hls source=fallback",
                    videoId,
                    elapsedMsSince(directStartedAt),
                    first.extraction.elapsedMs,
                    totalElapsedMs,
                )
                return@coroutineScope first.extraction.result
            }

            val direct = if (first.source == AttemptSource.DIRECT) {
                first.extraction
            } else {
                directAttempt.await()
            }
            resolveDirect(
                url = url,
                videoId = videoId,
                direct = direct,
                totalStartedAt = totalStartedAt,
                fallbackAttempt = fallbackAttempt,
                completedFallback = first.extraction.takeIf { first.source == AttemptSource.FALLBACK },
                fallbackStartedAt = fallbackStartedAt,
            )
        } finally {
            directAttempt.cancel()
            fallbackAttempt.cancel()
        }
    }

    private suspend fun resolveDirect(
        url: String,
        videoId: String,
        direct: TimedExtraction,
        totalStartedAt: Long,
        fallbackAttempt: Deferred<TimedExtraction>? = null,
        completedFallback: TimedExtraction? = null,
        fallbackStartedAt: Long? = null,
    ): ExtractionResult<StreamResponse> = when (val directResult = direct.result) {
        is ExtractionResult.Success -> {
            if (directResult.data.isLive && directResult.data.hlsUrl.isNotBlank()) {
                logger.info(
                    "youtube_live_resolution videoId={} directMs={} fallbackMs={} totalMs={} result=direct_hls",
                    videoId,
                    direct.elapsedMs,
                    fallbackElapsedMs(completedFallback, fallbackStartedAt),
                    elapsedMsSince(totalStartedAt),
                )
                directResult
            } else {
                logger.info(
                    "youtube_live_resolution videoId={} directMs={} fallbackMs={} totalMs={} result=not_live",
                    videoId,
                    direct.elapsedMs,
                    fallbackElapsedMs(completedFallback, fallbackStartedAt),
                    elapsedMsSince(totalStartedAt),
                )
                ExtractionResult.Failure(
                    "No active YouTube live stream is available",
                    "live_stream_unavailable",
                )
            }
        }
        is ExtractionResult.BadRequest -> {
            logger.info(
                "youtube_live_resolution videoId={} directMs={} fallbackMs={} totalMs={} result=bad_request code={}",
                videoId,
                direct.elapsedMs,
                fallbackElapsedMs(completedFallback, fallbackStartedAt),
                elapsedMsSince(totalStartedAt),
                directResult.code,
            )
            directResult
        }
        is ExtractionResult.Failure -> resolveFailure(
            url = url,
            videoId = videoId,
            direct = direct,
            failure = directResult,
            totalStartedAt = totalStartedAt,
            fallbackAttempt = fallbackAttempt,
            completedFallback = completedFallback,
        )
    }

    private suspend fun resolveFailure(
        url: String,
        videoId: String,
        direct: TimedExtraction,
        failure: ExtractionResult.Failure,
        totalStartedAt: Long,
        fallbackAttempt: Deferred<TimedExtraction>?,
        completedFallback: TimedExtraction?,
    ): ExtractionResult<StreamResponse> {
        val fallback = fallbackService
        if (fallback == null) {
            logger.info(
                "youtube_live_resolution videoId={} directMs={} fallbackMs=0 totalMs={} result=direct_failure directCode={}",
                videoId,
                direct.elapsedMs,
                elapsedMsSince(totalStartedAt),
                failure.code,
            )
            return failure
        }

        val extraction = completedFallback ?: fallbackAttempt?.await() ?: timedExtraction(fallback, url)
        val liveFallback = (extraction.result as? ExtractionResult.Success)
            ?.takeIf { it.data.isLive && it.data.hlsUrl.isNotBlank() }
        val fallbackStatus = when (val result = extraction.result) {
            is ExtractionResult.Success -> if (liveFallback != null) "live_hls" else "not_live"
            is ExtractionResult.BadRequest -> "bad_request:${result.code}"
            is ExtractionResult.Failure -> "failure:${result.code}"
        }
        logger.info(
            "youtube_live_resolution videoId={} directMs={} fallbackMs={} totalMs={} directCode={} result={}",
            videoId,
            direct.elapsedMs,
            extraction.elapsedMs,
            elapsedMsSince(totalStartedAt),
            failure.code,
            fallbackStatus,
        )
        return liveFallback ?: failure
    }

    private suspend fun timedExtraction(
        service: StreamService,
        url: String,
        startedAt: Long = System.nanoTime(),
    ): TimedExtraction {
        val result = service.getStreamInfo(url)
        return TimedExtraction(result, elapsedMsSince(startedAt))
    }

    private fun ExtractionResult<StreamResponse>.isLiveHls(): Boolean =
        (this as? ExtractionResult.Success)?.data?.let { it.isLive && it.hlsUrl.isNotBlank() } == true

    private fun fallbackElapsedMs(completed: TimedExtraction?, startedAt: Long?): Long =
        completed?.elapsedMs ?: startedAt?.let(::elapsedMsSince) ?: 0

    private fun elapsedMsSince(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / NANOS_PER_MILLI

    private data class TimedExtraction(
        val result: ExtractionResult<StreamResponse>,
        val elapsedMs: Long,
    )

    private data class CompletedAttempt(
        val source: AttemptSource,
        val extraction: TimedExtraction,
    )

    private enum class AttemptSource { DIRECT, FALLBACK }

    private companion object {
        const val HEDGE_DELAY_MILLIS = 100L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
