package dev.typetype.server.services

import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterItem

private val VALID_YOUTUBE_CHANNEL_TAB_SORTS = setOf("latest", "popular", "oldest")

fun String?.toYouTubeChannelTabSortFilter(): List<FilterItem>? {
    val normalized = this?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    if (normalized !in VALID_YOUTUBE_CHANNEL_TAB_SORTS) throw IllegalArgumentException("Invalid 'sort' parameter")
    return listOf(FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, normalized))
}
