package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.HomeRecommendationTaggedVideo

object HomeRecommendationItemFixtures {
    val context: HomeRecommendationSessionContext = HomeRecommendationSessionContext(
        intent = HomeRecommendationSessionIntent.AUTO,
        deviceClass = HomeRecommendationDeviceClass.UNKNOWN,
    )

    fun video(id: String, channel: String, title: String = id): VideoItem = VideoItem(
        id = id,
        title = title,
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 1,
        viewCount = 0,
        uploadDate = "",
        uploaded = 0,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )

    fun tagged(video: VideoItem, source: HomeRecommendationSourceTag): HomeRecommendationTaggedVideo =
        HomeRecommendationTaggedVideo(video = video, source = source)

    fun profile(
        seenUrls: Set<String> = emptySet(),
        blockedVideos: Set<String> = emptySet(),
        blockedChannels: Set<String> = emptySet(),
        blockedKeywords: Set<String> = emptySet(),
        subscriptionChannels: Set<String> = emptySet(),
        keywordAffinity: Set<String> = emptySet(),
        subscriptionEngagement: Double = 0.0,
        discoveryEngagement: Double = 0.0,
        personalizationEnabled: Boolean = true,
    ): HomeRecommendationProfile = HomeRecommendationProfile(
        seenUrls = seenUrls,
        blockedVideos = blockedVideos,
        blockedChannels = blockedChannels,
        blockedKeywords = blockedKeywords,
        feedbackBlockedVideos = emptySet(),
        feedbackBlockedChannels = emptySet(),
        subscriptionChannels = subscriptionChannels,
        favoriteUrls = emptySet(),
        watchLaterUrls = emptySet(),
        keywordAffinity = keywordAffinity,
        themeTokens = emptySet(),
        themeQueries = emptyList(),
        subscriptionEngagement = subscriptionEngagement,
        discoveryEngagement = discoveryEngagement,
        personalizationEnabled = personalizationEnabled,
    )
}
