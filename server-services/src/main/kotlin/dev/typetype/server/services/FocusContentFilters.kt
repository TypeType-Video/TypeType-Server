package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem

fun HomeRecommendationsResponse.filterFocus(profile: FocusProfile?): HomeRecommendationsResponse =
    if (profile == null) this else copy(
        items = items.filter { profile.keepsVideo(it.url, it.uploaderUrl) }
            .sortedBy { if (profile.isStudy(it.uploaderUrl)) 0 else 1 },
    )

fun FocusProfile.keepsFeedVideo(video: VideoItem): Boolean =
    (!isEntertainment(video.uploaderUrl) || withinArchive(video.publishedMillis())) &&
        keepsVideo(video.url, video.uploaderUrl)

fun SubscriptionFeedResponse.filterFocus(profile: FocusProfile?): SubscriptionFeedResponse =
    if (profile == null) this else copy(videos = videos.filter { profile.keepsFeedVideo(it) })

fun ChannelResponse.limitFocusArchive(profile: FocusProfile?, channelUrl: String, newestFirst: Boolean): ChannelResponse {
    val entertainment = profile != null &&
        (profile.isEntertainment(channelUrl) || videos.any { profile.isEntertainment(it.uploaderUrl) })
    if (!entertainment) return this
    val recent = videos.filter { profile.withinArchive(it.publishedMillis()) }
    val reachedArchive = recent.size < videos.size
    return copy(
        videos = recent,
        nextpage = nextpage.takeUnless { newestFirst && reachedArchive },
        focusArchiveDays = profile.archiveDays,
    )
}

private fun VideoItem.publishedMillis(): Long? = publishedAt ?: uploaded.takeIf { it > 0 }
