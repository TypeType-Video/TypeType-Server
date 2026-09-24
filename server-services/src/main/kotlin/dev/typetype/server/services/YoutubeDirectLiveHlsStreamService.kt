package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class YoutubeDirectLiveHlsStreamService(
    private val liveHlsService: StreamService,
) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        if (!isYoutubeUrl(url)) return ExtractionResult.BadRequest("Invalid YouTube URL")

        return when (val result = liveHlsService.getStreamInfo(url)) {
            is ExtractionResult.Success ->
                if (result.data.isLive && result.data.hlsUrl.isNotBlank()) {
                    result
                } else {
                    ExtractionResult.Failure("No active YouTube live stream is available", "live_stream_unavailable")
                }
            is ExtractionResult.BadRequest -> result
            is ExtractionResult.Failure -> result
        }
    }
}
