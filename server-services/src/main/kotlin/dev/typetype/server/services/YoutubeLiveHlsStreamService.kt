package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class YoutubeLiveHlsStreamService(
    private val metadataService: StreamService,
    private val liveHlsService: StreamService,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        if (!isYoutubeUrl(url)) return metadataService.getStreamInfo(url)

        val metadataResult = metadataService.getStreamInfo(url)
        val metadata = (metadataResult as? ExtractionResult.Success)?.data ?: return metadataResult
        if (!metadata.isLive) return metadataResult

        val liveResult = liveHlsService.getStreamInfo(url)
        val liveResponse = (liveResult as? ExtractionResult.Success)?.data
        return if (liveResponse?.isLive == true && liveResponse.hlsUrl.isNotBlank()) {
            liveResult
        } else {
            metadataResult
        }
    }
}
