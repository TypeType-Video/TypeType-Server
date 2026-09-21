package dev.typetype.server.services

import dev.typetype.server.models.SearchPageResponse
import org.schabi.newpipe.extractor.search.filter.FilterItem

enum class SearchContentKind {
    All,
    Videos,
    Playlists,
    Channels,
}

fun String?.toSearchContentKind(fallbackFilters: List<FilterItem>): SearchContentKind = this
    ?.substringAfterLast('|')
    ?.toSearchContentKind()
    ?.takeUnless { it == SearchContentKind.All }
    ?: fallbackFilters.toSearchContentKind()

fun List<FilterItem>.toSearchContentKind(): SearchContentKind = firstOrNull()
    ?.name
    ?.lowercase()
    ?.toSearchContentKind()
    ?: SearchContentKind.All

private fun String.toSearchContentKind(): SearchContentKind = when (lowercase()) {
    "channels", "music_artists" -> SearchContentKind.Channels
    "playlists", "music_playlists", "music_albums" -> SearchContentKind.Playlists
    "videos", "lives", "music_songs", "music_videos", "animes", "movies_and_tv" -> SearchContentKind.Videos
    else -> SearchContentKind.All
}

fun SearchPageResponse.filteredBy(kind: SearchContentKind): SearchPageResponse = when (kind) {
    SearchContentKind.All -> this
    SearchContentKind.Videos -> copy(playlists = emptyList(), channels = emptyList())
    SearchContentKind.Playlists -> copy(items = emptyList(), channels = emptyList())
    SearchContentKind.Channels -> copy(items = emptyList(), playlists = emptyList())
}
