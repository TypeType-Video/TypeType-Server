package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class YoutubePlayerClientFallbackStreamService(
    private val delegate: StreamService,
    private val clients: List<YoutubePlayerClient>,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        if (!isYoutubeUrl(url)) return delegate.getStreamInfo(url)
        var result: ExtractionResult<StreamResponse> = ExtractionResult.Failure("No YouTube player client configured")
        clients.forEach { client ->
            result = YoutubePlayerClientScope.withClient(client) { delegate.getStreamInfo(url) }
            if (result is ExtractionResult.Success) return result
        }
        return result
    }
}
