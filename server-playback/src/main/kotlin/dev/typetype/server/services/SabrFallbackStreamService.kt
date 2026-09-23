package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SabrFallbackStreamService(
    private val delegate: StreamService,
    private val sessionStore: SabrSessionStore,
    private val tokenSessionClient: TypetypeTokenYoutubeSessionClient,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = coroutineScope {
        if (!isYoutubeUrl(url)) return@coroutineScope delegate.getStreamInfo(url)
        val videoId = youtubeVideoId(url)
        val prepared = videoId?.let { async { sessionStore.fetchInfo(it, cachedFirst = true) } }
        val result = delegate.getStreamInfo(url)
        val response = (result as? ExtractionResult.Success)?.data
        if (response?.isLive == true) {
            prepared?.cancel()
            return@coroutineScope result
        }
        if (response == null) {
            if (result !is ExtractionResult.Failure || videoId == null) return@coroutineScope result
            prepared?.await()
            val session = tokenSessionClient.fetchPlaybackSession(videoId) ?: return@coroutineScope result
            return@coroutineScope ExtractionResult.Success(session.toFallbackStreamResponse(videoId))
        }
        if (response.hasSabrStreams() || videoId == null) return@coroutineScope result
        val playable = prepared?.await() ?: return@coroutineScope result
        ExtractionResult.Success(response.withSabrFallback(videoId, playable.info))
    }
}

private fun StreamResponse.hasSabrStreams(): Boolean =
    videoStreams.any { it.deliveryMethod == SABR_METHOD } ||
        videoOnlyStreams.any { it.deliveryMethod == SABR_METHOD } ||
        audioStreams.any { it.deliveryMethod == SABR_METHOD }

private const val SABR_METHOD = "sabr"
