package dev.typetype.server.services

import dev.typetype.server.models.RecommendationEventItem

object HomeRecommendationShortsSignals {
    fun shortSkipPenaltyByUrl(events: List<RecommendationEventItem>): Map<String, Double> {
        if (events.isEmpty()) return emptyMap()
        val grouped = events
            .take(500)
            .asSequence()
            .filter { it.videoUrl != null }
            .groupBy({ it.videoUrl!! }, { it })
        return grouped.mapValues { (_, list) ->
            val quickSkips = list.count { it.eventType == "short_skip" && (it.watchDurationMs ?: 0L) in 0L..800L }
            val normalSkips = list.count { it.eventType == "short_skip" && (it.watchDurationMs ?: 0L) in 801L..4_000L }
            val lateSkips = list.count { it.eventType == "short_skip" && (it.watchDurationMs ?: 0L) >= 4_001L }
            val deepWatches = list.count { it.eventType == "watch" && (it.watchRatio ?: 0.0) >= 0.75 }
            when {
                quickSkips >= 3 && deepWatches == 0 -> 0.18
                quickSkips >= 2 && deepWatches == 0 -> 0.30
                quickSkips + normalSkips >= 3 && deepWatches == 0 -> 0.42
                quickSkips + normalSkips + lateSkips >= 2 && deepWatches == 0 -> 0.62
                quickSkips + normalSkips + lateSkips >= 1 -> 0.82
                else -> 1.0
            }
        }
    }
}
