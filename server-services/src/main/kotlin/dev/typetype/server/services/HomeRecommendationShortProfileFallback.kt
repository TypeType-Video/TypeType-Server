package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationShortProfileFallback {
    fun inject(
        subscriptions: List<HomeRecommendationTaggedVideo>,
        profile: HomeRecommendationProfile,
    ): List<HomeRecommendationTaggedVideo> {
        if (subscriptions.isNotEmpty()) return subscriptions
        if (profile.watchLaterUrls.isEmpty()) return subscriptions
        return profile.watchLaterUrls
            .asSequence()
            .filter { it.contains("youtube.com") || it.contains("youtu.be") }
            .take(HomeRecommendationShortsSources.WATCH_LATER_SEED_LIMIT)
            .map { seed(it) }
            .toList()
    }

    private fun seed(url: String): HomeRecommendationTaggedVideo = HomeRecommendationTaggedVideo(
        video = VideoItem(
            id = url,
            title = "saved short",
            url = url,
            thumbnailUrl = "",
            uploaderName = "",
            uploaderUrl = "",
            uploaderAvatarUrl = "",
            duration = HomeRecommendationShortsDefaults.DEFAULT_SHORTS_DURATION,
            viewCount = 0,
            uploadDate = "",
            uploaded = -1L,
            streamType = "video_stream",
            isShortFormContent = true,
            uploaderVerified = false,
            shortDescription = null,
            publishedAt = null,
        ),
        source = HomeRecommendationSourceTag.SUBSCRIPTION,
    )
}
