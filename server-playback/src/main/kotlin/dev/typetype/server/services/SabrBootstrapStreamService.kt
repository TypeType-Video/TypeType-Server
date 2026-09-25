package dev.typetype.server.services

import dev.typetype.server.PlaybackTraceLog
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
        val metadataStartedAt = System.nanoTime()
        val metadataResult = tokenSessionClient.fetchPlaybackSession(videoId)
        PlaybackTraceLog.record(
            "sabr_bootstrap_metadata",
            "durationMs=${(System.nanoTime() - metadataStartedAt) / 1_000_000} result=${if (metadataResult == null) "miss" else "ready"}",
        )
        val metadata = metadataResult
            ?: return ExtractionResult.Failure("SABR bootstrap metadata unavailable")
        if (metadata.isLive) return liveHlsStreamService.getStreamInfo(url)
        val formatsStartedAt = System.nanoTime()
        val fromSession = metadata.preparedSabrInfo()
        val formatsResult = fromSession ?: sessionStore.fetchInfo(videoId, cachedFirst = true)
        PlaybackTraceLog.record(
            "sabr_bootstrap_formats",
            "durationMs=${(System.nanoTime() - formatsStartedAt) / 1_000_000} source=${if (fromSession != null) "session" else "cache_or_probe"} result=${if (formatsResult == null) "miss" else "ready"}",
        )
        val prepared = formatsResult
            ?: return ExtractionResult.Failure("SABR playback formats unavailable")
        sessionStore.rememberPreparedInfo(videoId, prepared)
        return ExtractionResult.Success(metadata.toFallbackStreamResponse(videoId))
    }
}

fun youtubeVideoId(url: String): String? =
    Regex("(?:[?&]v=|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
