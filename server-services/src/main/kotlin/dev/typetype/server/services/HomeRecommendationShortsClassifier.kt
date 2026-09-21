package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationShortsClassifier {
    fun isShort(video: VideoItem): Boolean {
        if (video.isShortFormContent) return true
        if (video.duration in 1L..85L) return true
        return video.url.contains("/shorts/")
    }
}
