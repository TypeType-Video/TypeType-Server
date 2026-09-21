package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchFiltersResponse
import dev.typetype.server.models.SearchPageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterItem

class PipePipeSearchService : SearchService {

    override suspend fun search(
        query: String,
        serviceId: Int,
        nextpage: String?,
        contentFilter: String?,
        filters: List<String>,
    ): ExtractionResult<SearchPageResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null

            val service = runCatching { NewPipe.getService(serviceId) }
                .getOrElse { return@withContext ExtractionResult.Failure(it.message ?: "Search failed") }
            val factory = service.searchQHFactory
            val selectedContentFilter = when (val resolution = factory.resolveContentFilter(contentFilter)) {
                is SearchFilterResolution.Valid -> resolution.items
                is SearchFilterResolution.Invalid ->
                    return@withContext ExtractionResult.BadRequest(resolution.message)
            }
            val availableFilters = factory.filtersFor(selectedContentFilter)
            val selectedFilters = if (filters.isEmpty()) {
                availableFilters.defaultSearchFilters()
            } else {
                when (val resolution = availableFilters.resolveSearchFilters(filters)) {
                    is SearchFilterResolution.Valid -> resolution.items
                    is SearchFilterResolution.Invalid ->
                        return@withContext ExtractionResult.BadRequest(resolution.message)
                }
            }
            val queryHandler = runCatching {
                factory.fromQuery(
                    query,
                    selectedContentFilter.ifEmpty { null },
                    selectedFilters.ifEmpty { null },
                )
            }.getOrElse {
                return@withContext ExtractionResult.Failure(it.message ?: "Search failed")
            }
            val contentKind = contentFilter.toSearchContentKind(selectedContentFilter)

            runCatching {
                withExtractionRetry {
                    withTimeout(30_000L) {
                        if (page == null) {
                            SearchInfo.getInfo(service, queryHandler).toSearchPageResponse().filteredBy(contentKind)
                        } else {
                            SearchInfo.getMoreItems(service, queryHandler, page).toSearchPageResponse().filteredBy(contentKind)
                        }
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Search failed") },
            )
        }

    override suspend fun filters(
        serviceId: Int,
        contentFilter: String?,
    ): ExtractionResult<SearchFiltersResponse> =
        withContext(Dispatchers.IO) {
            val factory = runCatching { NewPipe.getService(serviceId).searchQHFactory }
                .getOrElse { return@withContext ExtractionResult.Failure(it.message ?: "Search filters failed") }
            val selectedContentFilter = when (val resolution = factory.resolveContentFilter(contentFilter)) {
                is SearchFilterResolution.Valid -> resolution.items
                is SearchFilterResolution.Invalid ->
                    return@withContext ExtractionResult.BadRequest(resolution.message)
            }
            val availableFilters = factory.filtersFor(selectedContentFilter)
            runCatching {
                SearchFiltersResponse(
                    contentFilters = factory.availableContentFilter.toSearchContentFilterOptions(),
                    sortFilters = availableFilters.toSearchFilterOptions(),
                    filterGroups = availableFilters.toSearchFilterGroups(),
                )
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Search filters failed") },
            )
        }
}

private fun SearchQueryHandlerFactory.resolveContentFilter(value: String?): SearchFilterResolution {
    if (value == null) return SearchFilterResolution.Valid(availableContentFilter.defaultSearchFilter())
    val selected = availableContentFilter.findSearchFilter(value)
    return if (selected.isEmpty()) {
        SearchFilterResolution.Invalid("Unknown content filter")
    } else {
        SearchFilterResolution.Valid(selected)
    }
}

private fun SearchQueryHandlerFactory.filtersFor(contentFilter: List<FilterItem>): Filter? =
    contentFilter.firstOrNull()?.let { getContentFilterSortFilterVariant(it.identifier) }
        ?: availableSortFilter
