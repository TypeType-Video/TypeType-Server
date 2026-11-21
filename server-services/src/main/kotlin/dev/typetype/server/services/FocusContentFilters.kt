package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.PublicPlaylistResponse
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem

fun FocusProfile.keepsFeedVideo(video: VideoItem): Boolean =
    keepsFeedVideo(video.url, video.uploaderUrl, video.publishedMillis())

fun List<VideoItem>.filterFocus(profile: FocusProfile?): List<VideoItem> =
    if (profile == null) this else filter { profile.keepsFeedVideo(it) }

fun HomeRecommendationsResponse.filterFocus(profile: FocusProfile?): HomeRecommendationsResponse =
    if (profile == null) this else copy(
        items = items.filter { profile.keepsFeedVideo(it) }
            .sortedBy { if (profile.isStudy(it.uploaderUrl)) 0 else 1 },
    )

fun SubscriptionFeedResponse.filterFocus(profile: FocusProfile?): SubscriptionFeedResponse =
    if (profile == null) this else copy(videos = videos.filter { profile.keepsFeedVideo(it) })

fun StreamResponse.filterFocus(profile: FocusProfile?): StreamResponse =
    if (profile == null) this else copy(relatedStreams = relatedStreams.filter { profile.keepsFeedVideo(it) })

fun PublicPlaylistResponse.filterFocus(profile: FocusProfile?): PublicPlaylistResponse =
    if (profile == null) this else copy(videos = videos.filter { profile.keepsFeedVideo(it) })

fun ChannelResponse.limitFocusArchive(profile: FocusProfile?, channelUrl: String, newestFirst: Boolean): ChannelResponse {
    if (profile == null) return this
    val resolvedUploaderUrl = videos.firstOrNull { it.uploaderUrl.isNotBlank() }?.uploaderUrl
    val isEntertainment = profile.isEntertainment(channelUrl) || videos.any { profile.isEntertainment(it.uploaderUrl) }
    if (isEntertainment) {
        val recent = videos.filter { profile.withinArchive(it.publishedMillis()) }
        val reachedArchive = recent.size < videos.size
        return copy(
            videos = recent,
            nextpage = nextpage.takeUnless { newestFirst && reachedArchive },
            focusArchiveDays = profile.archiveDays,
        )
    }
    if (profile.isStudy(channelUrl) || (resolvedUploaderUrl != null && profile.isStudy(resolvedUploaderUrl))) {
        return this
    }
    val unlockAt = profile.pendingUnlockAt(channelUrl)
        ?: resolvedUploaderUrl?.let { profile.pendingUnlockAt(it) }
    return copy(
        videos = emptyList(),
        nextpage = null,
        focusBlocked = true,
        focusStudyUnlockAt = unlockAt,
        focusBlockCode = if (unlockAt != null) "focus_study_pending" else "focus_channel_blocked",
    )
}

fun ChannelResponse.resolveChannelKey(requestedUrl: String): String =
    videos.firstOrNull { it.uploaderUrl.isNotBlank() }?.uploaderUrl ?: requestedUrl

private fun VideoItem.publishedMillis(): Long? = publishedAt ?: uploaded.takeIf { it > 0 }
