package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

fun VideoItem.isUpcomingAt(now: Long): Boolean =
    !isPostLive && RssVideoMetadata.publishedAtMillis(this) > now

fun VideoItem.isLiveOrUpcomingAt(now: Long): Boolean = isLive || isUpcomingAt(now)

fun VideoItem.isLiveContentOrUpcomingAt(now: Long): Boolean =
    isLive || isPostLive || isLiveContent || isUpcomingAt(now)
