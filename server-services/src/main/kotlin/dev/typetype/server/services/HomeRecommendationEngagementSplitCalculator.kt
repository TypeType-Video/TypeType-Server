package dev.typetype.server.services

import dev.typetype.server.models.RecommendationEventItem

object HomeRecommendationEngagementSplitCalculator {
    fun compute(
        events: List<RecommendationEventItem>,
        subscriptionChannels: Set<String>,
    ): HomeRecommendationEngagementSplit {
        var sub = 0.0
        var disc = 0.0
        events.take(300).forEach { event ->
            val isSub = event.uploaderUrl?.let { it in subscriptionChannels } ?: false
            val score = when (event.eventType) {
                "click" -> 1.0
                "watch" -> if ((event.watchRatio ?: 0.0) >= 0.5) 1.3 else 0.6
                "impression" -> 0.1
                "short_skip" -> -0.8
                else -> 0.0
            }
            if (isSub) sub += score else disc += score
        }
        return HomeRecommendationEngagementSplit(subscriptionEngagement = sub, discoveryEngagement = disc)
    }
}
