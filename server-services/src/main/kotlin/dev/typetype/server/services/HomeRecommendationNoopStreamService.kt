package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

object HomeRecommendationNoopStreamService : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        ExtractionResult.Failure("Stream service unavailable")
}
