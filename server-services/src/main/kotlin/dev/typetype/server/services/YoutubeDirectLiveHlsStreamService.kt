package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import org.slf4j.LoggerFactory

class YoutubeDirectLiveHlsStreamService(
    private val liveHlsService: StreamService,
    private val fallbackService: StreamService? = null,
) : StreamService {
    private val logger = LoggerFactory.getLogger(YoutubeDirectLiveHlsStreamService::class.java)

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        if (!isYoutubeUrl(url)) return ExtractionResult.BadRequest("Invalid YouTube URL")

        val videoId = youtubeVideoId(url) ?: "unknown"
        val directStartedAt = System.nanoTime()
        val directResult = liveHlsService.getStreamInfo(url)
        val directElapsedMs = elapsedMsSince(directStartedAt)

        return when (directResult) {
            is ExtractionResult.Success -> {
                if (directResult.data.isLive && directResult.data.hlsUrl.isNotBlank()) {
                    logger.info(
                        "youtube_live_resolution videoId={} directMs={} fallbackMs=0 result=direct_hls",
                        videoId,
                        directElapsedMs,
                    )
                    directResult
                } else {
                    logger.info(
                        "youtube_live_resolution videoId={} directMs={} fallbackMs=0 result=not_live",
                        videoId,
                        directElapsedMs,
                    )
                    ExtractionResult.Failure(
                        "No active YouTube live stream is available",
                        "live_stream_unavailable",
                    )
                }
            }
            is ExtractionResult.BadRequest -> {
                logger.info(
                    "youtube_live_resolution videoId={} directMs={} fallbackMs=0 result=bad_request code={}",
                    videoId,
                    directElapsedMs,
                    directResult.code,
                )
                directResult
            }
            is ExtractionResult.Failure ->
                fallbackIfLiveHls(url, videoId, directElapsedMs, directResult)
        }
    }

    private suspend fun fallbackIfLiveHls(
        url: String,
        videoId: String,
        directElapsedMs: Long,
        failure: ExtractionResult.Failure,
    ): ExtractionResult<StreamResponse> {
        val fallback = fallbackService
        if (fallback == null) {
            logger.info(
                "youtube_live_resolution videoId={} directMs={} fallbackMs=0 result=direct_failure directCode={}",
                videoId,
                directElapsedMs,
                failure.code,
            )
            return failure
        }

        val fallbackStartedAt = System.nanoTime()
        val fallbackResult = fallback.getStreamInfo(url)
        val fallbackElapsedMs = elapsedMsSince(fallbackStartedAt)
        val liveFallback = (fallbackResult as? ExtractionResult.Success)
            ?.takeIf { it.data.isLive && it.data.hlsUrl.isNotBlank() }
        val fallbackStatus = when (fallbackResult) {
            is ExtractionResult.Success -> if (liveFallback != null) "live_hls" else "not_live"
            is ExtractionResult.BadRequest -> "bad_request:${fallbackResult.code}"
            is ExtractionResult.Failure -> "failure:${fallbackResult.code}"
        }
        logger.info(
            "youtube_live_resolution videoId={} directMs={} fallbackMs={} totalMs={} directCode={} result={}",
            videoId,
            directElapsedMs,
            fallbackElapsedMs,
            directElapsedMs + fallbackElapsedMs,
            failure.code,
            fallbackStatus,
        )
        return liveFallback ?: failure
    }

    private fun elapsedMsSince(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
