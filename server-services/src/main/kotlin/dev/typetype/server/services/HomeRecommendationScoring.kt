package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationScoring {
    fun scoreSubscription(
        video: VideoItem,
        profile: HomeRecommendationProfile,
        context: HomeRecommendationSessionContext,
    ): Double {
        val base = scoreSubscription(video, profile)
        return base + contextBoost(video, context)
    }

    fun scoreDiscovery(
        video: VideoItem,
        profile: HomeRecommendationProfile,
        context: HomeRecommendationSessionContext,
    ): Double {
        val base = scoreDiscovery(video, profile)
        return base + contextBoost(video, context)
    }

    fun scoreShortsDiscovery(video: VideoItem, profile: HomeRecommendationProfile, context: HomeRecommendationSessionContext): Double =
        scoreDiscovery(video, profile, context)

    fun scoreSubscription(video: VideoItem, profile: HomeRecommendationProfile): Double {
        return 1.5 + commonSignals(video, profile)
    }

    fun scoreDiscovery(video: VideoItem, profile: HomeRecommendationProfile): Double {
        return 1.0 + commonSignals(video, profile)
    }

    private fun commonSignals(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val recency = recencyBoost(video.uploaded)
        val subscriptionBoost = if (video.uploaderUrl in profile.subscriptionChannels) 0.2 else 0.0
        val favoriteBoost = if (video.url in profile.favoriteUrls) 0.15 else 0.0
        val watchLaterBoost = if (video.url in profile.watchLaterUrls) 0.08 else 0.0
        val livePenalty = if (HomeRecommendationLiveTitleDetector.isLiveLike(video.title)) -0.5 else 0.0
        return recency + subscriptionBoost + favoriteBoost + watchLaterBoost + livePenalty
    }

    private fun recencyBoost(uploaded: Long): Double {
        if (uploaded <= 0) return 0.0
        val ageHours = (System.currentTimeMillis() - uploaded).coerceAtLeast(0L) / 3_600_000.0
        return 1.0 / (1.0 + ageHours / 60.0)
    }

    private fun contextBoost(video: VideoItem, context: HomeRecommendationSessionContext): Double =
        when (context.intent) {
            HomeRecommendationSessionIntent.QUICK -> if (video.duration in 1L..600L) 0.05 else 0.0
            HomeRecommendationSessionIntent.DEEP -> if (video.duration >= 1_200L) 0.05 else 0.0
            HomeRecommendationSessionIntent.AUTO -> 0.0
        }

}
