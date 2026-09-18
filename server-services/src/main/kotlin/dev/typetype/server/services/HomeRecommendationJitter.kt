package dev.typetype.server.services

import java.util.Random
import kotlin.math.max

object HomeRecommendationJitter {
    fun apply(
        scored: List<HomeRecommendationScoredVideo>,
        feedHistory: Map<String, RecommendationFeedHistoryEntry>,
    ): List<HomeRecommendationScoredVideo> {
        if (scored.size <= 1) return scored
        val overlap = scored.count { it.video.url in feedHistory }.toDouble() / scored.size.toDouble()
        val magnitude = when {
            overlap > 0.5 -> 0.12
            overlap > 0.2 -> 0.06
            else -> 0.02
        }
        val random = Random(System.currentTimeMillis() / 1_800_000L)
        return scored
            .map { item -> item.copy(score = item.score + random.nextDouble() * magnitude) }
            .sortedWith(compareByDescending<HomeRecommendationScoredVideo> { it.score }.thenBy { it.video.url })
    }
}
