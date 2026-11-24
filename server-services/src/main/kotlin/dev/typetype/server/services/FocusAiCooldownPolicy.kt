package dev.typetype.server.services

import kotlin.math.roundToLong

object FocusAiCooldownPolicy {
    private const val MIN_SCORE = 0
    private const val MAX_SCORE = 100
    private const val FULL_PENALTY_THRESHOLD = 10
    private const val MS_PER_MINUTE = 60_000L

    fun isApproved(score: Int, passScore: Int = FocusAiConfig.DEFAULT_PASS_SCORE): Boolean =
        score > passScore

    fun cooldownMs(
        score: Int,
        passScore: Int = FocusAiConfig.DEFAULT_PASS_SCORE,
        maxCooldownMinutes: Int = FocusAiConfig.DEFAULT_MAX_COOLDOWN_MINUTES,
    ): Long {
        val clamped = score.coerceIn(MIN_SCORE, MAX_SCORE)
        if (clamped > passScore) return 0L
        if (clamped <= FULL_PENALTY_THRESHOLD) return maxCooldownMinutes.toLong() * MS_PER_MINUTE
        val span = (passScore - FULL_PENALTY_THRESHOLD).coerceAtLeast(1).toDouble()
        val ratio = (passScore - clamped).toDouble() / span
        val minutes = (ratio * maxCooldownMinutes).roundToLong().coerceIn(1L, maxCooldownMinutes.toLong())
        return minutes * MS_PER_MINUTE
    }
}
