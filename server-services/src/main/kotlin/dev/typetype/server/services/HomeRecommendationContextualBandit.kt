package dev.typetype.server.services

import dev.typetype.server.models.RecommendationEventItem
import java.time.DayOfWeek
import java.time.LocalDateTime

object HomeRecommendationContextualBandit {
    fun weightBySource(
        events: List<RecommendationEventItem>,
        sourceByUrl: Map<String, HomeRecommendationSourceTag>,
        serviceId: Int,
        sessionIntent: HomeRecommendationSessionIntent,
        deviceClass: HomeRecommendationDeviceClass,
        now: LocalDateTime = LocalDateTime.now(),
    ): Map<HomeRecommendationSourceTag, Double> {
        val context = contextKey(serviceId = serviceId, intent = sessionIntent, deviceClass = deviceClass, now = now)
        val scoped = events.take(280).filter { it.contextKey == context }
        if (scoped.isEmpty()) return emptyMap()
        val byVideo = scoped
            .asSequence()
            .mapNotNull { event -> event.videoUrl?.takeIf { it.isNotBlank() }?.let { it to event } }
            .groupBy({ it.first }, { it.second })
        val scoreBySource = HomeRecommendationSourceTag.entries.associateWith { 1.0 }.toMutableMap()
        byVideo.forEach { (videoUrl, grouped) ->
            val source = sourceByUrl[videoUrl] ?: HomeRecommendationSourceTag.DISCOVERY_EXPLORATION
            scoreBySource[source] = (scoreBySource[source] ?: 1.0) + reward(grouped)
        }
        val max = scoreBySource.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        return scoreBySource.mapValues { (_, value) -> (value / max).coerceIn(0.45, 1.55) }
    }

    fun contextKey(
        serviceId: Int,
        intent: HomeRecommendationSessionIntent,
        deviceClass: HomeRecommendationDeviceClass,
        now: LocalDateTime,
    ): String {
        val day = if (isWeekend(now.dayOfWeek)) "we" else "wk"
        val bucket = when (now.hour) {
            in 0..5 -> "night"
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            else -> "evening"
        }
        val intentKey = when (intent) {
            HomeRecommendationSessionIntent.AUTO -> "auto"
            HomeRecommendationSessionIntent.QUICK -> "quick"
            HomeRecommendationSessionIntent.DEEP -> "deep"
        }
        val deviceKey = when (deviceClass) {
            HomeRecommendationDeviceClass.MOBILE -> "mobile"
            HomeRecommendationDeviceClass.DESKTOP -> "desktop"
            HomeRecommendationDeviceClass.TABLET -> "tablet"
            HomeRecommendationDeviceClass.TV -> "tv"
            HomeRecommendationDeviceClass.UNKNOWN -> "unknown"
        }
        return "$serviceId:$day:$bucket:$intentKey:$deviceKey"
    }

    private fun reward(events: List<RecommendationEventItem>): Double {
        val clicks = events.count { it.eventType == "click" }
        val watches = events.count { it.eventType == "watch" && (it.watchRatio ?: 0.0) >= 0.4 }
        val impressions = events.count { it.eventType == "impression" }.coerceAtLeast(1)
        val skips = events.count { it.eventType == "short_skip" }
        return ((clicks + watches) * 1.6 - skips * 0.8) / impressions.toDouble()
    }

    private fun isWeekend(day: DayOfWeek): Boolean = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
}
