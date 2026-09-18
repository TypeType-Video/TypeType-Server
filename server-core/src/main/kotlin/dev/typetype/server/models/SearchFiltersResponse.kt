package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchFiltersResponse(
    val contentFilters: List<SearchFilterOption>,
    val sortFilters: List<SearchFilterOption>,
    val filterGroups: List<SearchFilterGroup>,
)
