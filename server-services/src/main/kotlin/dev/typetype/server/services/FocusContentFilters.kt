package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem

fun HomeRecommendationsResponse.filterFocus(profile: FocusProfile?): HomeRecommendationsResponse =
    if (profile == null) this else copy(items = items.filter { profile.keepsVideo(it.url, it.uploaderUrl) })

fun SubscriptionFeedResponse.filterFocus(profile: FocusProfile?): SubscriptionFeedResponse =
    if (profile == null) this else copy(videos = videos.filter { profile.keepsVideo(it.url, it.uploaderUrl) })

fun ChannelResponse.limitFocusArchive(profile: FocusProfile?, channelUrl: String, newestFirst: Boolean): ChannelResponse {
    if (profile == null || !profile.isEntertainment(channelUrl)) return this
    val recent = videos.filter { profile.withinArchive(it.publishedMillis()) }
    val reachedArchive = recent.size < videos.size
    return copy(
        videos = recent,
        nextpage = nextpage.takeUnless { newestFirst && reachedArchive },
        focusArchiveDays = profile.archiveDays,
    )
}

private fun VideoItem.publishedMillis(): Long? = publishedAt ?: uploaded.takeIf { it > 0 }
