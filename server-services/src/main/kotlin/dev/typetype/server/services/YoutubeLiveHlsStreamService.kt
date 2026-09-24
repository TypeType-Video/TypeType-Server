package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class YoutubeLiveHlsStreamService(
    private val metadataService: StreamService,
    private val liveHlsService: StreamService,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        if (!isYoutubeUrl(url)) return metadataService.getStreamInfo(url)

        val liveResult = liveHlsService.getStreamInfo(url)
        val liveResponse = (liveResult as? ExtractionResult.Success)?.data
        if (liveResponse?.isLive == true && !liveResponse.requiresMembership && liveResponse.hlsUrl.isNotBlank()) {
            return liveResult
        }

        return metadataService.getStreamInfo(url)
    }
}
