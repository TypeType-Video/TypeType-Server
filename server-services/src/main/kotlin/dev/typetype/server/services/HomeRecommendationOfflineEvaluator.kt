package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationOfflineEvaluator {
    fun evaluate(
        ranked: List<VideoItem>,
        clickedUrls: Set<String>,
        trainWindow: Int = 20,
        testWindow: Int = 10,
    ): HomeRecommendationMetrics {
        if (ranked.isEmpty()) {
            return HomeRecommendationMetrics(0.0, 0.0, 0.0)
        }
        val train = ranked.take(trainWindow)
        val test = ranked.drop(trainWindow).take(testWindow)
        val evalSlice = if (test.isNotEmpty()) test else ranked.take(testWindow)
        return HomeRecommendationMetrics(
            ndcgAt10 = ndcgAtK(evalSlice, clickedUrls, testWindow),
            diversityAt10 = diversityAtK(evalSlice),
            duplicateRateAt10 = duplicateRateAtK(evalSlice),
        )
    }

    private fun ndcgAtK(top: List<VideoItem>, clickedUrls: Set<String>, k: Int): Double {
        if (top.isEmpty()) return 0.0
        val dcg = top.mapIndexed { index, video ->
            val rel = if (video.url in clickedUrls) 1.0 else 0.0
            rel / log2(index + 2.0)
        }.sum()
        val idealCount = minOf(k, clickedUrls.size)
        val idcg = (0 until idealCount).sumOf { index -> 1.0 / log2(index + 2.0) }
        if (idcg <= 0.0) return 0.0
        return (dcg / idcg).coerceIn(0.0, 1.0)
    }

    private fun diversityAtK(top: List<VideoItem>): Double {
        if (top.isEmpty()) return 0.0
        val uniqueChannels = top.map { it.uploaderUrl.ifBlank { it.uploaderName } }.toSet().size
        return uniqueChannels.toDouble() / top.size.toDouble()
    }

    private fun duplicateRateAtK(top: List<VideoItem>): Double {
        if (top.isEmpty()) return 0.0
        val duplicates = top.size - top.map { it.url }.toSet().size
        return duplicates.toDouble() / top.size.toDouble()
    }

    private fun log2(value: Double): Double = kotlin.math.log(value, 2.0)
}
