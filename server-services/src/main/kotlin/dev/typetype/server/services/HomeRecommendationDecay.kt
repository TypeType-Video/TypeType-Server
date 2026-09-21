package dev.typetype.server.services

object HomeRecommendationDecay {
    fun eventRecencyPenalty(occurredAt: Long, now: Long = System.currentTimeMillis()): Double {
        if (occurredAt <= 0L) return 1.0
        val ageHours = (now - occurredAt).coerceAtLeast(0L).toDouble() / 3_600_000.0
        return when {
            ageHours < 24.0 -> 1.0
            ageHours < 72.0 -> 0.9
            ageHours < 168.0 -> 0.75
            ageHours < 336.0 -> 0.6
            else -> 0.45
        }
    }
}
