package dev.typetype.server.services

import dev.typetype.server.models.RecommendationEventItem
import kotlin.math.ln
import kotlin.math.sqrt

object HomeRecommendationExplorationBandit {
    fun apply(
        events: List<RecommendationEventItem>,
        sourceWeights: Map<HomeRecommendationSourceTag, Double>,
        sourceByUrl: Map<String, HomeRecommendationSourceTag>,
    ): Map<HomeRecommendationSourceTag, Double> {
        if (events.isEmpty()) return sourceWeights
        val bySource = mutableMapOf<HomeRecommendationSourceTag, SourceStats>()
        val grouped = events.take(320)
            .asSequence()
            .mapNotNull { e -> e.videoUrl?.takeIf { it.isNotBlank() }?.let { it to e } }
            .groupBy({ it.first }, { it.second })
        grouped.forEach { (url, videoEvents) ->
            val source = sourceByUrl[url] ?: HomeRecommendationSourceTag.DISCOVERY_EXPLORATION
            val impressions = videoEvents.count { it.eventType == "impression" }.coerceAtLeast(1)
            val reward = videoEvents.count { it.eventType == "click" || it.eventType == "watch" }
            val current = bySource[source] ?: SourceStats(0, 0.0)
            bySource[source] = SourceStats(
                pulls = current.pulls + impressions,
                reward = current.reward + reward,
            )
        }
        val totalPulls = bySource.values.sumOf { it.pulls }.coerceAtLeast(1)
        return HomeRecommendationSourceTag.entries.associateWith { source ->
            val base = sourceWeights[source] ?: 1.0
            val stats = bySource[source]
            if (stats == null || stats.pulls <= 0) return@associateWith (base + 0.10).coerceIn(0.45, 1.6)
            val mean = stats.reward / stats.pulls.toDouble()
            val ucb = mean + sqrt((2.0 * ln(totalPulls.toDouble())) / stats.pulls.toDouble())
            (base + ucb * 0.08).coerceIn(0.45, 1.6)
        }
    }

    private data class SourceStats(
        val pulls: Int,
        val reward: Double,
    )
}
