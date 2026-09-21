package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem

interface TrendingService {
    suspend fun getTrending(serviceId: Int): ExtractionResult<List<VideoItem>>
}
