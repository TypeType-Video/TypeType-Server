package dev.typetype.server.services

import dev.typetype.server.models.SearchFilterGroup
import dev.typetype.server.models.SearchFilterOption
import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterGroup
import org.schabi.newpipe.extractor.search.filter.FilterItem

sealed interface SearchFilterResolution {
    data class Valid(val items: List<FilterItem>) : SearchFilterResolution
    data class Invalid(val message: String) : SearchFilterResolution
}

fun Filter?.toSearchContentFilterOptions(): List<SearchFilterOption> = entries()
    .mapIndexed { index, entry -> entry.toOption(isDefault = index == 0, includeGroup = true) }

fun Filter?.toSearchFilterOptions(): List<SearchFilterOption> = this?.filterGroups
    ?.flatMap { group ->
        group.filterItems.mapIndexed { index, item ->
            FilterEntry(group, item).toOption(group.onlyOneCheckable && index == 0, includeGroup = true)
        }
    }.orEmpty()

fun Filter?.toSearchFilterGroups(): List<SearchFilterGroup> = this?.filterGroups
    ?.map { group ->
        SearchFilterGroup(
            key = "${group.groupName.orEmpty()}|${group.identifier}",
            label = group.groupName.orEmpty(),
            multiSelect = !group.onlyOneCheckable,
            options = group.filterItems.mapIndexed { index, item ->
                FilterEntry(group, item).toOption(group.onlyOneCheckable && index == 0)
            },
        )
    }.orEmpty()

fun Filter?.findSearchFilter(value: String?): List<FilterItem> = value?.let { raw ->
    entries().firstOrNull { it.value == raw }?.let { listOf(it.item) }
} ?: emptyList()

fun Filter?.resolveSearchFilters(values: List<String>): SearchFilterResolution {
    if (values.isEmpty()) return SearchFilterResolution.Valid(emptyList())
    val requested = values.toSet()
    val selected = entries().filter { it.value in requested }
    if (selected.size != requested.size) return SearchFilterResolution.Invalid("Unknown search filter")
    val conflict = selected.groupBy { it.group.identifier }
        .values
        .firstOrNull { entries -> entries.first().group.onlyOneCheckable && entries.size > 1 }
    if (conflict != null) {
        val groupName = conflict.first().group.groupName.orEmpty().ifBlank { "filter" }
        return SearchFilterResolution.Invalid("Only one '$groupName' filter can be selected")
    }
    return SearchFilterResolution.Valid(selected.map(FilterEntry::item))
}

fun Filter?.defaultSearchFilters(): List<FilterItem> = this?.filterGroups
    ?.filter(FilterGroup::onlyOneCheckable)
    ?.mapNotNull { it.filterItems.firstOrNull() }
    .orEmpty()

fun Filter?.defaultSearchFilter(): List<FilterItem> = this?.filterGroups
    ?.firstOrNull()
    ?.filterItems
    ?.firstOrNull()
    ?.let { listOf(it) }
    ?: emptyList()

private data class FilterEntry(val group: FilterGroup, val item: FilterItem) {
    val value: String = item.searchFilterValue(group.groupName.orEmpty())

    fun toOption(isDefault: Boolean, includeGroup: Boolean = false): SearchFilterOption {
        val groupName = group.groupName.orEmpty()
        val label = if (includeGroup && groupName.isNotBlank()) "$groupName: ${item.name}" else item.name
        return SearchFilterOption(value = value, label = label, isDefault = isDefault)
    }
}

private fun Filter?.entries(): List<FilterEntry> = this?.filterGroups
    ?.flatMap { group -> group.filterItems.map { item -> FilterEntry(group, item) } }
    .orEmpty()

private fun FilterItem.searchFilterValue(groupName: String): String =
    listOf(groupName, identifier.toString(), name).joinToString("|")
