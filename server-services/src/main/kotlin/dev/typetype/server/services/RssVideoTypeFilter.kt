package dev.typetype.server.services

import dev.typetype.server.models.RssFeedItem
import dev.typetype.server.models.VideoItem

object RssVideoTypeFilter {
    fun includes(feed: RssFeedItem, video: VideoItem, now: Long): Boolean = when {
        video.isLive -> feed.includeLive
        video.isUpcomingAt(now) -> feed.includeUpcoming
        video.isShortFormContent -> feed.includeShorts
        else -> feed.includeVideos
    }
}
