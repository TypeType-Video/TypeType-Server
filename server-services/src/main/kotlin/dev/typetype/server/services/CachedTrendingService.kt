package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import kotlinx.serialization.builtins.ListSerializer

class CachedTrendingService(
    private val delegate: TrendingService,
    private val cache: CacheService,
) : TrendingService {

    private val listSerializer = ListSerializer(VideoItem.serializer())

    override suspend fun getTrending(serviceId: Int): ExtractionResult<List<VideoItem>> = PublicExtractionCache.getOrLoad(
        cache = cache,
        area = PublicCacheArea("trending", listSerializer) { videos ->
            PublicCacheAdmissions.firstPage(null, videos.isEmpty(), PublicCachePolicy.trendingTtl(serviceId))
        },
        key = PublicCacheKey.of("trending", serviceId.toString()),
    ) { delegate.getTrending(serviceId) }
}
