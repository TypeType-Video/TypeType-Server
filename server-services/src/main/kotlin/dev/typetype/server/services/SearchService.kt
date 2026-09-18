package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchFiltersResponse
import dev.typetype.server.models.SearchPageResponse

interface SearchService {
    suspend fun search(
        query: String,
        serviceId: Int,
        nextpage: String? = null,
        contentFilter: String? = null,
        filters: List<String> = emptyList(),
    ): ExtractionResult<SearchPageResponse>

    suspend fun filters(
        serviceId: Int,
        contentFilter: String? = null,
    ): ExtractionResult<SearchFiltersResponse>
}
