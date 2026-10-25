package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult

class CachedChannelService(
    private val delegate: ChannelService,
    private val cache: CacheService,
) : ChannelService {

    override suspend fun getChannel(url: String, nextpage: String?, sort: String?): ExtractionResult<ChannelResponse> {
        val cacheArea = if (url.isBilibiliChannelUrl()) BILIBILI_CHANNEL_CACHE_AREA else "channel"
        val ttlSeconds = PublicCachePolicy.channelTtl(url, nextpage, sort)
        return PublicExtractionCache.getOrLoad(
            cache = cache,
            area = PublicCacheArea(
                cacheArea,
                ChannelResponse.serializer(),
                keepLastGood = nextpage == null,
                admission = PublicCacheAdmissions.channel(url, nextpage, ttlSeconds),
            ),
            key = PublicCacheKey.of(cacheArea, url, nextpage, sort),
        ) { delegate.getChannel(url, nextpage, sort) }
    }

    override suspend fun getPlaylists(url: String, nextpage: String?): ExtractionResult<ChannelPlaylistsResponse> {
        val ttlSeconds = PublicCachePolicy.channelTtl(url, nextpage, null)
        return PublicExtractionCache.getOrLoad(
            cache = cache,
            area = PublicCacheArea("channel-playlists", ChannelPlaylistsResponse.serializer(), keepLastGood = nextpage == null) {
                CacheAdmission.Valid(ttlSeconds)
            },
            key = PublicCacheKey.of("channel-playlists", url, nextpage),
        ) { delegate.getPlaylists(url, nextpage) }
    }

    private fun String.isBilibiliChannelUrl(): Boolean = recommendationServiceId(this) == BILIBILI_SERVICE_ID

    private companion object {
        const val BILIBILI_CHANNEL_CACHE_AREA = "channel-bilibili-v3"
    }
}
