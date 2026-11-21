package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult

class CachedChannelVideosSource(
    private val channelService: ChannelService,
    private val tabVideos: ChannelVideosSource,
    private val cache: CacheService,
) : ChannelVideosSource {
    override suspend fun firstPage(url: String): ExtractionResult<ChannelResponse> {
        if (url.toChannelTab(null) == null) return channelService.getChannel(url, null)
        val result = PublicExtractionCache.getOrLoad(
            cache = cache,
            area = PublicCacheArea(
                CACHE_AREA,
                ChannelResponse.serializer(),
                admission = PublicCacheAdmissions.channelVideos(url, PublicCachePolicy.feedTabTtl(url)),
            ),
            key = PublicCacheKey.of(CACHE_AREA, url),
        ) { tabVideos.firstPage(url) }
        return if (result is ExtractionResult.Success) {
            ExtractionResult.Success(result.data.withFallbackUploaderUrl(url))
        } else {
            result
        }
    }

    private companion object {
        const val CACHE_AREA = "channel-videos"
    }
}
