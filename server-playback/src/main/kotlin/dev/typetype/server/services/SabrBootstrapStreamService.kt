package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class SabrBootstrapStreamService(
    private val sessionStore: SabrSessionStore,
    private val tokenSessionClient: TypetypeTokenYoutubeSessionClient,
    private val liveHlsStreamService: StreamService,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        val videoId = youtubeVideoId(url)
            ?: return ExtractionResult.BadRequest("Invalid YouTube URL")
        val metadata = tokenSessionClient.fetchPlaybackSession(videoId)
            ?: return ExtractionResult.Failure("SABR bootstrap metadata unavailable")
        if (metadata.isLive) return liveHlsStreamService.getStreamInfo(url)
        val prepared = metadata.preparedSabrInfo()
            ?: sessionStore.fetchInfo(videoId, cachedFirst = true)
            ?: return ExtractionResult.Failure("SABR playback formats unavailable")
        sessionStore.rememberPreparedInfo(videoId, prepared)
        return ExtractionResult.Success(metadata.toFallbackStreamResponse(videoId))
    }
}

fun youtubeVideoId(url: String): String? =
    Regex("(?:[?&]v=|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
