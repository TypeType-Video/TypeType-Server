package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem

class YoutubeScopedTrendingService(private val delegate: TrendingService) : TrendingService {
    override suspend fun getTrending(serviceId: Int): ExtractionResult<List<VideoItem>> =
        if (serviceId == YOUTUBE_SERVICE_ID) {
            YoutubeSessionTokenScope.withoutCredentials { delegate.getTrending(serviceId) }
        } else {
            delegate.getTrending(serviceId)
        }
}
