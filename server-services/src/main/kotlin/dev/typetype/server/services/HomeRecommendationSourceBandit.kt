package dev.typetype.server.services

import dev.typetype.server.models.RecommendationEventItem

object HomeRecommendationSourceBandit {
    fun weightBySource(
        events: List<RecommendationEventItem>,
        sourceByUrl: Map<String, HomeRecommendationSourceTag>,
    ): Map<HomeRecommendationSourceTag, Double> {
        val byVideo = events.take(200)
            .asSequence()
            .mapNotNull { event -> event.videoUrl?.takeIf { it.isNotBlank() }?.let { it to event } }
            .groupBy({ it.first }, { it.second })
        val scoreBySource = HomeRecommendationSourceTag.entries.associateWith { 1.0 }.toMutableMap()
        byVideo.forEach { (videoUrl, events) ->
            val source = sourceByUrl[videoUrl] ?: HomeRecommendationSourceTag.DISCOVERY_EXPLORATION
            scoreBySource[source] = (scoreBySource[source] ?: 1.0) + sourceReward(events)
        }
        val max = scoreBySource.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        return scoreBySource.mapValues { (_, value) -> (value / max).coerceIn(0.45, 1.5) }
    }

    private fun sourceReward(events: List<RecommendationEventItem>): Double {
        val clicks = events.count { it.eventType == "click" }
        val watches = events.count { it.eventType == "watch" && (it.watchRatio ?: 0.0) >= 0.4 }
        val impressions = events.count { it.eventType == "impression" }.coerceAtLeast(1)
        val skips = events.count { it.eventType == "short_skip" }
        return ((clicks + watches) * 1.6 - skips * 0.8) / impressions.toDouble()
    }
}
