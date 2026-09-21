package dev.typetype.server.services

import dev.typetype.server.models.SearchPageResponse
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

fun SearchInfo.toSearchPageResponse(): SearchPageResponse = SearchPageResponse(
    items = relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
    nextpage = nextPage?.toCursor(),
    searchSuggestion = searchSuggestion?.takeIf { it.isNotBlank() },
    isCorrectedSearch = isCorrectedSearch,
    playlists = relatedItems.filterIsInstance<PlaylistInfoItem>().map { it.toPlaylistResultItem() },
    channels = relatedItems.filterIsInstance<ChannelInfoItem>().map { it.toChannelResultItem() },
)

fun InfoItemsPage<InfoItem>.toSearchPageResponse(): SearchPageResponse = SearchPageResponse(
    items = items.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
    nextpage = nextPage?.toCursor(),
    searchSuggestion = null,
    isCorrectedSearch = false,
    playlists = items.filterIsInstance<PlaylistInfoItem>().map { it.toPlaylistResultItem() },
    channels = items.filterIsInstance<ChannelInfoItem>().map { it.toChannelResultItem() },
)
