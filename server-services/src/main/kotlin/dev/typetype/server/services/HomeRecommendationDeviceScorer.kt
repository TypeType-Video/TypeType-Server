package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationDeviceScorer {
    fun bonus(video: VideoItem, context: HomeRecommendationSessionContext): Double = when (context.deviceClass) {
        HomeRecommendationDeviceClass.MOBILE -> mobileBonus(video.duration)
        HomeRecommendationDeviceClass.TABLET -> tabletBonus(video.duration)
        HomeRecommendationDeviceClass.TV -> tvBonus(video.duration)
        HomeRecommendationDeviceClass.DESKTOP -> desktopBonus(video.duration)
        HomeRecommendationDeviceClass.UNKNOWN -> 0.0
    }

    private fun mobileBonus(duration: Long): Double = when {
        duration in 45..600 -> 0.12
        duration >= 2_400 -> -0.10
        else -> 0.0
    }

    private fun tabletBonus(duration: Long): Double = when {
        duration in 300..1_200 -> 0.08
        duration >= 2_700 -> 0.04
        else -> 0.0
    }

    private fun tvBonus(duration: Long): Double = when {
        duration >= 900 -> 0.14
        duration in 30..240 -> -0.08
        else -> 0.0
    }

    private fun desktopBonus(duration: Long): Double = when {
        duration in 300..1_800 -> 0.08
        else -> 0.0
    }
}
