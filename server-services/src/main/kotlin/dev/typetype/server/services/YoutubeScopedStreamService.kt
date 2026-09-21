package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class YoutubeScopedStreamService(private val delegate: StreamService) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        if (isYoutubeUrl(url)) {
            YoutubeSessionTokenScope.withoutCredentials { delegate.getStreamInfo(url) }
        } else {
            delegate.getStreamInfo(url)
        }
}
