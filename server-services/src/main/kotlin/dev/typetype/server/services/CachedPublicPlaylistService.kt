package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistResponse

class CachedPublicPlaylistService(
    private val delegate: PublicPlaylistService,
    private val cache: CacheService,
) : PublicPlaylistService {
    override suspend fun getPlaylist(url: String, nextpage: String?): ExtractionResult<PublicPlaylistResponse> =
        PublicExtractionCache.getOrLoad(
            cache = cache,
            area = "playlist",
            key = PublicCacheKey.of("playlist", url, nextpage),
            serializer = PublicPlaylistResponse.serializer(),
            ttlSeconds = { PublicCachePolicy.playlistTtl(nextpage) },
        ) { delegate.getPlaylist(url, nextpage) }
}
