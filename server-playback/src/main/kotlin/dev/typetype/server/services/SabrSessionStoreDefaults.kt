package dev.typetype.server.services

import java.time.Duration

object SabrSessionStoreDefaults {
    const val INFO_TIMEOUT_MS = 8_000L
    const val INFO_ATTEMPTS = 1
    const val INFO_RETRY_DELAY_MS = 250L

    fun maxSessions(): Int =
        System.getenv("SABR_MAX_SESSIONS")?.toIntOrNull()?.coerceAtLeast(1) ?: 24

    /** How recently a viewer must have asked a session for data for it to count as playing. */
    fun playingWindow(): Duration = Duration.ofSeconds(
        System.getenv("SABR_PLAYING_WINDOW_SECONDS")?.toLongOrNull()?.coerceIn(10L, 600L) ?: 30L,
    )

    /** Bytes all sessions may hold before a playing session is evicted for room: a third of the heap by default. */
    fun byteBudget(): Long =
        System.getenv("SABR_SESSION_BYTE_BUDGET_MB")?.toLongOrNull()?.coerceAtLeast(64L)?.times(1_048_576L)
            ?: (Runtime.getRuntime().maxMemory() / 3)

    fun idleEviction(): Duration = Duration.ofSeconds(
        System.getenv("SABR_IDLE_EVICTION_SECONDS")?.toLongOrNull()?.coerceAtLeast(60L) ?: 240L,
    )

    fun Long.toStartTimeSecs(): Int? {
        if (this <= 0L) return null
        return (this / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
