package dev.typetype.server.services

import dev.typetype.server.PlaybackTraceLog
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class SabrBootstrapStreamService(
    private val sessionStore: SabrSessionStore,
    private val tokenSessionClient: TypetypeTokenYoutubeSessionClient,
    private val liveHlsStreamService: StreamService,
    private val cache: SabrBootstrapCache = SabrBootstrapCache(),
) : StreamService {
    private val singleFlight = KeyedSingleFlight<String, ExtractionResult<StreamResponse>>()

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        val videoId = youtubeVideoId(url)
            ?: return ExtractionResult.BadRequest("Invalid YouTube URL")
        cache.get(videoId)?.let { cached ->
            PlaybackTraceLog.record("sabr_bootstrap_cache", "result=hit")
            return ExtractionResult.Success(cached)
        }
        return singleFlight.run(videoId) { cache.get(videoId)?.let { ExtractionResult.Success(it) } ?: load(url, videoId) }
    }

    private suspend fun load(url: String, videoId: String): ExtractionResult<StreamResponse> {
        val epoch = cache.currentEpoch()
        val metadataStartedAt = System.nanoTime()
        val metadataResult = tokenSessionClient.fetchPlaybackSessionResult(videoId)
        PlaybackTraceLog.record(
            "sabr_bootstrap_metadata",
            "durationMs=${(System.nanoTime() - metadataStartedAt) / 1_000_000} result=${if (metadataResult is TokenServiceResult.Success) "ready" else "miss"}",
        )
        val metadata = when (metadataResult) {
            is TokenServiceResult.Success -> metadataResult.value
            is TokenServiceResult.Failure -> return metadataResult.toBootstrapFailure()
        }
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
        val response = metadata.toFallbackStreamResponse(videoId)
        if (prepared.info.visitorData == metadata.token?.visitorData) {
            sessionStore.rememberPreparedInfo(videoId, prepared)
            cache.put(videoId, response, epoch)
        }
        return ExtractionResult.Success(response)
    }

    private fun TokenServiceResult.Failure.toBootstrapFailure(): ExtractionResult.Failure = when {
        isProviderRefusal() -> ExtractionResult.Failure(
            "YouTube is refusing requests from this server right now",
            PROVIDER_ACCESS_BLOCKED_CODE,
            ExtractionFailureKind.ProviderAccessBlocked,
        )
        isTransient() -> ExtractionResult.Failure(
            "The token service did not answer in time",
            TOKEN_SERVICE_UNAVAILABLE_CODE,
            ExtractionFailureKind.ProviderAccessBlocked,
        )
        else -> ExtractionResult.Failure("SABR bootstrap metadata unavailable")
    }

    private companion object {
        const val PROVIDER_ACCESS_BLOCKED_CODE = "provider_access_blocked"
        const val TOKEN_SERVICE_UNAVAILABLE_CODE = "token_service_unavailable"
    }
}

fun youtubeVideoId(url: String): String? =
    Regex("(?:[?&]v=|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
