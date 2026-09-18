package dev.typetype.server.services

import java.time.DayOfWeek
import java.time.LocalDateTime

object HomeRecommendationTemporalBoost {
    private val morningKeywords = setOf("news", "daily", "podcast", "brief", "update")
    private val eveningKeywords = setOf("music", "entertainment", "gaming", "movie", "relax")
    private val weekendKeywords = setOf("travel", "vlog", "diy", "cooking", "review")

    fun boost(title: String, now: LocalDateTime = LocalDateTime.now()): Double {
        val tokens = RecommendationTopicTokenizer.tokenize(title)
        if (tokens.isEmpty()) return 0.0
        var boost = 0.0
        if (now.hour in 6..11 && tokens.any { it in morningKeywords }) boost += 0.08
        if (now.hour in 18..23 && tokens.any { it in eveningKeywords }) boost += 0.08
        if (isWeekend(now.dayOfWeek) && tokens.any { it in weekendKeywords }) boost += 0.06
        return boost.coerceIn(0.0, 0.18)
    }

    private fun isWeekend(day: DayOfWeek): Boolean = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
}
