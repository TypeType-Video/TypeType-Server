package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class YoutubePlayerClientStreamService(
    private val delegate: StreamService,
    private val client: YoutubePlayerClient,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        if (isYoutubeUrl(url)) {
            YoutubePlayerClientScope.withClient(client) { delegate.getStreamInfo(url) }
        } else {
            delegate.getStreamInfo(url)
        }
}
