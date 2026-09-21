package dev.typetype.server.services

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.RecommendationEventItem
import kotlin.math.abs

object HomeRecommendationSignalProfileBuilder {
    fun buildChannelTopicProfile(historyItems: List<HistoryItem>): Map<String, Map<String, Double>> {
        if (historyItems.isEmpty()) return emptyMap()
        return historyItems
            .groupBy { it.channelUrl }
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, items) ->
                val total = items.size.toDouble().coerceAtLeast(1.0)
                items.asSequence()
                    .flatMap { RecommendationTopicTokenizer.tokenize(it.title).asSequence() }
                    .groupingBy { it }
                    .eachCount()
                    .mapValues { (_, count) -> (count.toDouble() / total).coerceIn(0.0, 1.0) }
                    .entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .associate { it.key to it.value }
            }
            .filterValues { it.isNotEmpty() }
    }

    fun buildShortsTopicInterest(events: List<RecommendationEventItem>): Map<String, Double> {
        if (events.isEmpty()) return emptyMap()
        val scoreByTopic = mutableMapOf<String, Double>()
        events.take(500).forEach { event ->
            val title = event.title ?: return@forEach
            val tokens = RecommendationTopicTokenizer.tokenize(title)
            if (tokens.isEmpty()) return@forEach
            val delta = when (event.eventType) {
                "short_skip" -> when {
                    (event.watchDurationMs ?: 0L) < 800L -> -1.2
                    (event.watchDurationMs ?: 0L) < 5_000L -> -0.8
                    else -> -0.3
                }
                "watch" -> if ((event.watchRatio ?: 0.0) >= 0.5) 0.8 else 0.3
                "click" -> 0.25
                else -> 0.0
            }
            if (delta == 0.0) return@forEach
            tokens.forEach { token -> scoreByTopic[token] = (scoreByTopic[token] ?: 0.0) + delta }
        }
        return scoreByTopic
            .entries
            .sortedByDescending { abs(it.value) }
            .take(80)
            .associate { it.key to it.value.coerceIn(-6.0, 6.0) }
    }

    fun buildTopicPairPenalty(events: List<RecommendationEventItem>): Map<String, Double> {
        if (events.isEmpty()) return emptyMap()
        val counts = mutableMapOf<String, Double>()
        events.take(500)
            .filter { it.eventType == "short_skip" }
            .forEach { event ->
                val title = event.title ?: return@forEach
                val agePenalty = HomeRecommendationDecay.eventRecencyPenalty(event.occurredAt)
                HomeRecommendationTopicPairs.fromTitle(title)
                    .forEach { pair ->
                        counts[pair] = (counts[pair] ?: 0.0) + agePenalty
                    }
            }
        return counts
            .mapValues { (_, count) -> when {
                count >= 5.5 -> 0.45
                count >= 3.0 -> 0.70
                count >= 1.8 -> 0.85
                else -> 1.0
            } }
            .filterValues { it < 1.0 }
    }

    fun buildCreatorMomentum(events: List<RecommendationEventItem>): Map<String, Double> {
        if (events.isEmpty()) return emptyMap()
        val scoreByUploader = mutableMapOf<String, Double>()
        events.take(360).forEach { event ->
            val uploader = event.uploaderUrl ?: return@forEach
            val delta = when (event.eventType) {
                "watch" -> if ((event.watchRatio ?: 0.0) >= 0.5) 0.8 else 0.3
                "click" -> 0.35
                "favorite", "watch_later_add" -> 0.9
                "short_skip" -> -0.6
                else -> 0.0
            }
            if (delta == 0.0) return@forEach
            scoreByUploader[uploader] = (scoreByUploader[uploader] ?: 0.0) + delta
        }
        return scoreByUploader
            .entries
            .sortedByDescending { abs(it.value) }
            .take(80)
            .associate { it.key to it.value.coerceIn(-6.0, 6.0) }
    }
}
