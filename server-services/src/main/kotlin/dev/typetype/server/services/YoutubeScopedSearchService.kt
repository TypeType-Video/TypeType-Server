package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchFiltersResponse
import dev.typetype.server.models.SearchPageResponse

class YoutubeScopedSearchService(private val delegate: SearchService) : SearchService {
    override suspend fun search(
        query: String,
        serviceId: Int,
        nextpage: String?,
        contentFilter: String?,
        filters: List<String>,
    ): ExtractionResult<SearchPageResponse> =
        if (serviceId == YOUTUBE_SERVICE_ID) {
            YoutubeSessionTokenScope.withoutCredentials {
                delegate.search(query, serviceId, nextpage, contentFilter, filters)
            }
        } else {
            delegate.search(query, serviceId, nextpage, contentFilter, filters)
        }

    override suspend fun filters(
        serviceId: Int,
        contentFilter: String?,
    ): ExtractionResult<SearchFiltersResponse> = delegate.filters(serviceId, contentFilter)
}
