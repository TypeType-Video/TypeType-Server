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
        area = "search",
        key = PublicCacheKey.of(
            "search-v3",
            serviceId.toString(),
            query,
            nextpage,
            contentFilter,
            *filters.sorted().toTypedArray(),
        ),
        serializer = SearchPageResponse.serializer(),
        ttlSeconds = { PublicCachePolicy.searchTtl(serviceId, nextpage) },
    ) { delegate.search(query, serviceId, nextpage, contentFilter, filters) }

    override suspend fun filters(
        serviceId: Int,
        contentFilter: String?,
    ): ExtractionResult<SearchFiltersResponse> = delegate.filters(serviceId, contentFilter)
}
