package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchFiltersResponse
import dev.typetype.server.models.SearchPageResponse

class CachedSearchService(
    private val delegate: SearchService,
    private val cache: CacheService,
) : SearchService {

    override suspend fun search(
        query: String,
        serviceId: Int,
        nextpage: String?,
        contentFilter: String?,
        filters: List<String>,
    ): ExtractionResult<SearchPageResponse> = PublicExtractionCache.getOrLoad(
        cache = cache,
        area = PublicCacheArea("search", SearchPageResponse.serializer(), keepLastGood = false) {
            val empty = it.items.isEmpty() && it.playlists.isEmpty() && it.channels.isEmpty()
            PublicCacheAdmissions.firstPage(nextpage, empty, PublicCachePolicy.searchTtl(serviceId, nextpage))
        },
        key = PublicCacheKey.of(
            "search-v4",
            serviceId.toString(),
            query,
            nextpage,
            contentFilter,
            *filters.sorted().toTypedArray(),
        ),
    ) { delegate.search(query, serviceId, nextpage, contentFilter, filters) }

    override suspend fun filters(
        serviceId: Int,
        contentFilter: String?,
    ): ExtractionResult<SearchFiltersResponse> = delegate.filters(serviceId, contentFilter)
}
