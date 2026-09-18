package dev.typetype.server.services

import dev.typetype.server.models.RecommendationEventItem

object HomeRecommendationEventAnalyzer {
    fun buildSignals(events: List<RecommendationEventItem>): HomeRecommendationEngagementSignals {
        if (events.isEmpty()) return HomeRecommendationEngagementSignals(emptyMap(), emptySet(), emptyMap(), emptyMap())
        val now = System.currentTimeMillis()
        val byVideo = events
            .asSequence()
            .mapNotNull { event -> event.videoUrl?.takeIf { it.isNotBlank() }?.let { it to event } }
            .groupBy({ it.first }, { it.second })
        val byTopic = mutableMapOf<String, Int>()
        val byChannel = mutableMapOf<String, Int>()
        val penalties = mutableMapOf<String, Double>()
        val blocked = mutableSetOf<String>()
        byVideo.forEach { (videoUrl, videoEvents) ->
            val clicks = videoEvents.count { it.eventType == "click" || it.eventType == "watch" }
            val watch = videoEvents.firstOrNull { it.eventType == "watch" }
            val watchRatio = watch?.watchRatio ?: 0.0
            val shortSkips = videoEvents.filter { it.eventType == "short_skip" }
            val recentImpressions = videoEvents.count {
                it.eventType == "impression" && (now - it.occurredAt) <= IMPLICIT_WINDOW_MS
            }
            if (clicks == 0 && recentImpressions >= BLOCK_THRESHOLD) {
                blocked += videoUrl
                penalties[videoUrl] = HEAVY_PENALTY
            } else if (clicks == 0 && recentImpressions >= LIGHT_THRESHOLD) {
                penalties[videoUrl] = MEDIUM_PENALTY
            } else if (watchRatio > 0.0 && watchRatio < 0.15) {
                penalties[videoUrl] = SHORT_WATCH_PENALTY
            }
            if (clicks == 0 && shortSkips.isNotEmpty()) {
                val maxSkipDepth = shortSkips.maxOf { skipPenaltyByDuration(it.watchDurationMs) }
                penalties[videoUrl] = minOf(penalties[videoUrl] ?: 1.0, maxSkipDepth)
            }
            if (clicks == 0) {
                val channel = videoEvents.firstOrNull()?.uploaderUrl.orEmpty()
                if (channel.isNotBlank()) {
                    byChannel[channel] = (byChannel[channel] ?: 0) + shortSkips.size
                }
                videoEvents.mapNotNull { it.title }
                    .flatMap { RecommendationTopicTokenizer.tokenize(it) }
                    .forEach { token -> byTopic[token] = (byTopic[token] ?: 0) + shortSkips.size }
            }
        }
        val topicPenalty = byTopic.mapValues { (_, count) -> topicPenalty(count) }.filterValues { it < 1.0 }
        val channelPenalty = byChannel.mapValues { (_, count) -> channelPenalty(count) }.filterValues { it < 1.0 }
        return HomeRecommendationEngagementSignals(
            videoPenalty = penalties,
            implicitBlockedVideos = blocked,
            rejectionTopicPenalty = topicPenalty,
            rejectionChannelPenalty = channelPenalty,
        )
    }

    private fun skipPenaltyByDuration(durationMs: Long?): Double = when {
        durationMs == null -> 0.80
        durationMs < 800L -> 0.25
        durationMs < 5_000L -> 0.45
        else -> 0.75
    }

    private fun topicPenalty(count: Int): Double = when {
        count >= 7 -> 0.35
        count >= 4 -> 0.55
        count >= 2 -> 0.8
        else -> 1.0
    }

    private fun channelPenalty(count: Int): Double = when {
        count >= 6 -> 0.4
        count >= 3 -> 0.65
        else -> 1.0
    }

    private const val IMPLICIT_WINDOW_MS = 48L * 60L * 60L * 1000L
    private const val BLOCK_THRESHOLD = 5
    private const val LIGHT_THRESHOLD = 3
    private const val HEAVY_PENALTY = 0.10
    private const val MEDIUM_PENALTY = 0.30
    private const val SHORT_WATCH_PENALTY = 0.45
}
