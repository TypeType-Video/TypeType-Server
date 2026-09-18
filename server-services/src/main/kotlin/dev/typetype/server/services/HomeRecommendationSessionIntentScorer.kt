package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationSessionIntentScorer {
    fun bonus(video: VideoItem, context: HomeRecommendationSessionContext): Double = when (context.intent) {
        HomeRecommendationSessionIntent.AUTO -> 0.0
        HomeRecommendationSessionIntent.QUICK -> quickBonus(video)
        HomeRecommendationSessionIntent.DEEP -> deepBonus(video)
    }

    private fun quickBonus(video: VideoItem): Double {
        val duration = video.duration
        return when {
            duration in 60..540 -> 0.20
            duration in 541..840 -> 0.10
            duration > 2_400 -> -0.14
            else -> 0.0
        }
    }

    private fun deepBonus(video: VideoItem): Double {
        val duration = video.duration
        return when {
            duration >= 1_200 -> 0.22
            duration in 900..1_199 -> 0.12
            duration in 30..240 -> -0.12
            else -> 0.0
        }
    }
}
