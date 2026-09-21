package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationPoolScorer {
    fun discovery(
        video: VideoItem,
        profile: HomeRecommendationProfile,
        context: HomeRecommendationSessionContext,
        shortsMode: Boolean,
    ): Double {
        return if (shortsMode) {
            HomeRecommendationScoring.scoreShortsDiscovery(video, profile, context)
        } else {
            HomeRecommendationScoring.scoreDiscovery(video, profile, context)
        }
    }
}
