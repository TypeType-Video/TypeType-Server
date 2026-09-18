package dev.typetype.server.services

import java.time.Duration

object SabrSessionStoreDefaults {
    const val INFO_TIMEOUT_MS = 8_000L
    const val INFO_ATTEMPTS = 1
    const val INFO_RETRY_DELAY_MS = 250L

    fun maxSessions(): Int =
        System.getenv("SABR_MAX_SESSIONS")?.toIntOrNull()?.coerceAtLeast(1) ?: 24

    fun idleEviction(): Duration = Duration.ofSeconds(
        System.getenv("SABR_IDLE_EVICTION_SECONDS")?.toLongOrNull()?.coerceAtLeast(60L) ?: 240L,
    )

    fun Long.toStartTimeSecs(): Int? {
        if (this <= 0L) return null
        return (this / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
