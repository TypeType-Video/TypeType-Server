package dev.typetype.server.services

object HomeRecommendationFeedHistoryPenalty {
    fun penalty(entry: RecommendationFeedHistoryEntry?, now: Long = System.currentTimeMillis()): Double {
        if (entry == null) return 1.0
        val hoursSince = ((now - entry.lastShown).coerceAtLeast(0L)) / 3_600_000.0
        val countPenalty = when {
            entry.showCount >= 5 -> 0.7
            entry.showCount >= 3 -> 0.85
            else -> 1.0
        }
        val timePenalty = when {
            hoursSince < 2.0 -> 0.05
            hoursSince < 8.0 -> 0.15
            hoursSince < 24.0 -> 0.35
            hoursSince < 72.0 -> 0.60
            hoursSince < 168.0 -> 0.80
            hoursSince < 336.0 -> 0.92
            else -> 1.0
        }
        return (timePenalty * countPenalty).coerceIn(0.05, 1.0)
    }
}
