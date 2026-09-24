package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class SabrFallbackStreamService(
    private val delegate: StreamService,
    private val sessionStore: SabrSessionStore,
    private val tokenSessionClient: TypetypeTokenYoutubeSessionClient,
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
            return ExtractionResult.Success(session.toFallbackStreamResponse(videoId))
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
