package dev.typetype.server.services

object HomeRecommendationShortsSeenMemory {
    fun penalty(
        entry: RecommendationFeedHistoryEntry?,
        now: Long = System.currentTimeMillis(),
    ): Double {
        if (entry == null) return 1.0
        val hoursSince = ((now - entry.lastShown).coerceAtLeast(0L)) / 3_600_000.0
        return when {
            entry.showCount >= 4 && hoursSince < 24.0 -> 0.05
            entry.showCount >= 3 && hoursSince < 48.0 -> 0.10
            entry.showCount >= 2 && hoursSince < 72.0 -> 0.22
            entry.showCount >= 1 && hoursSince < 36.0 -> 0.45
            else -> 1.0
        }
    }
}
