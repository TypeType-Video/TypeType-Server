package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

const val YOUTUBE_SESSION_REQUIRED_CODE = "youtube_session_required"
const val YOUTUBE_SESSION_REQUIRED_ERROR = "Connect YouTube to access this video"

class YoutubeSessionSabrStreamService(
    private val metadataService: YoutubeSessionStreamService,
    private val infoService: AuthenticatedSabrInfoService,
    private val timeoutMs: Long = AuthenticatedSabrPolicy.STREAM_TIMEOUT_MS,
) {
    suspend fun getStreamInfo(userId: String, url: String): ExtractionResult<StreamResponse>? {
        return try {
            withTimeout(timeoutMs) {
                val metadata = metadataService.getStreamInfo(userId, url) ?: return@withTimeout null
                if (metadata !is ExtractionResult.Success) return@withTimeout metadata
                if (metadata.data.isLive) return@withTimeout null
                val videoId = youtubeVideoId(url) ?: return@withTimeout ExtractionResult.BadRequest("Invalid YouTube URL")
                when (val info = infoService.fetch(userId, videoId)) {
                    is AuthenticatedSabrInfoResult.Ready ->
                        ExtractionResult.Success(metadata.data.withSabrFallback(videoId, info.prepared.info))
                    AuthenticatedSabrInfoResult.Failed -> {
                        logger.warn("authenticated_sabr_stream event=sabr_failed_fallback_to_standard videoId={}", videoId)
                        ExtractionResult.Success(metadata.data)
                    }
                    AuthenticatedSabrInfoResult.TimedOut -> {
                        logger.warn("authenticated_sabr_stream event=sabr_timeout_fallback_to_standard videoId={}", videoId)
                        ExtractionResult.Success(metadata.data)
                    }
                    AuthenticatedSabrInfoResult.NotConnected -> null
                }
            }
        } catch (error: TimeoutCancellationException) {
            logger.warn("authenticated_sabr_stream event=overall_timeout_transient_failure url={}", url)
            ExtractionResult.Failure("Timed out loading authenticated YouTube stream")
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(YoutubeSessionSabrStreamService::class.java)
    }
}

fun ExtractionResult<StreamResponse>.requiresYoutubeSession(): Boolean =
    when (this) {
        is ExtractionResult.Success -> data.requiresMembership
        is ExtractionResult.BadRequest -> code == "age_restricted" || code == "members_only"
        is ExtractionResult.Failure -> false
    }
