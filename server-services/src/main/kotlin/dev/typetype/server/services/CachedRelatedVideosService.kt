package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import kotlinx.serialization.builtins.ListSerializer

class CachedRelatedVideosService(
    private val delegate: RelatedVideosService,
    private val cache: CacheService,
) : RelatedVideosService {
    private val area = PublicCacheArea(CACHE_PREFIX, ListSerializer(VideoItem.serializer())) { videos ->
        PublicCacheAdmissions.firstPage(null, videos.isEmpty(), CACHE_TTL_SECONDS)
    }

    override suspend fun related(videoUrl: String): ExtractionResult<List<VideoItem>> {
        val videoId = youtubeVideoId(videoUrl) ?: return delegate.related(videoUrl)
        return PublicExtractionCache.getOrLoad(cache, area, "$CACHE_PREFIX:$videoId") { delegate.related(videoUrl) }
    }

    private companion object {
        const val CACHE_PREFIX = "youtube-related:v1"
        const val CACHE_TTL_SECONDS = 300L
    }
}
