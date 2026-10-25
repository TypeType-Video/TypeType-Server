package dev.typetype.server.services

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterItem

fun StreamingService.channelTabExtractor(
    url: String,
    channelId: String,
    tab: String,
    sort: String?,
): ChannelTabExtractor {
    if (tab == ChannelTabs.SEARCH) return getChannelTabExtractor(channelTabLHFactory.fromUrl(url))
    val contentFilter = listOf(FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab))
    val linkHandler = channelTabLHFactory.fromQuery(channelId, contentFilter, sort.toYouTubeChannelTabSortFilter())
    return getChannelTabExtractor(linkHandler)
}

fun StreamingService.toChannelTab(url: String, sort: String?): String? {
    if (channelTabLHFactory == null) return null
    val tab = url.toChannelTab(sort)
    return if (tab == ChannelTabs.VIDEOS && serviceId != ServiceList.YouTube.serviceId) null else tab
}
