package dev.typetype.server.services

import dev.typetype.server.PlaybackTraceLog
import dev.typetype.server.models.EndScreenItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class SabrFallbackStreamService(
    private val delegate: StreamService,
    private val sessionStore: SabrSessionStore,
    private val tokenSessionClient: TypetypeTokenYoutubeSessionClient,
    private val endScreens: suspend (videoId: String, visitorData: String) -> List<EndScreenItem> = { _, _ -> emptyList() },
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        if (!isYoutubeUrl(url)) return delegate.getStreamInfo(url)
        val videoId = youtubeVideoId(url)
        val result = delegate.getStreamInfo(url)
        val response = (result as? ExtractionResult.Success)?.data
        if (response?.isLive == true || response?.hasSabrStreams() == true) {
            return result
        }
        if (response == null) {
            if (result !is ExtractionResult.Failure || videoId == null) return result
            val session = tokenSessionClient.fetchPlaybackSession(videoId) ?: return result
            val elements = session.info.visitorData?.takeIf { it.isNotBlank() }
                ?.let { endScreens(videoId, it) }
                .orEmpty()
            PlaybackTraceLog.record("sabr_fallback_end_screens", "count=${elements.size}")
            return ExtractionResult.Success(session.toFallbackStreamResponse(videoId).copy(endScreenElements = elements))
        }
        if (videoId == null) return result
        val playable = sessionStore.fetchInfo(videoId, cachedFirst = true) ?: return result
        return ExtractionResult.Success(response.withSabrFallback(videoId, playable.info))
    }
}

private fun StreamResponse.hasSabrStreams(): Boolean =
    videoStreams.any { it.deliveryMethod == SABR_METHOD } ||
        videoOnlyStreams.any { it.deliveryMethod == SABR_METHOD } ||
        audioStreams.any { it.deliveryMethod == SABR_METHOD }

private const val SABR_METHOD = "sabr"
