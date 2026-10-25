package dev.typetype.server.services

import java.util.concurrent.ConcurrentHashMap

class SubscriptionFeedRefreshPlanner(
    private val clock: () -> Long,
    private val intervalMs: Long = SubscriptionFeedSettings.freshnessMs,
    private val activeWindowMs: Long = SubscriptionFeedSettings.activeWindowMs,
) {
    private val lastReadAt = ConcurrentHashMap<String, Long>()
    private val lastAttemptAt = ConcurrentHashMap<String, Long>()

    fun touch(userId: String) {
        lastReadAt[userId] = clock()
    }

    fun recordAttempt(userId: String) {
        lastAttemptAt[userId] = clock()
    }

    fun activeUsers(): List<String> {
        val now = clock()
        lastReadAt.entries.removeIf { now - it.value >= activeWindowMs }
        lastAttemptAt.keys.retainAll(lastReadAt.keys)
        return lastReadAt.keys.toList()
    }

    fun canRetryCold(userId: String): Boolean {
        val attempted = lastAttemptAt[userId] ?: return true
        return clock() - attempted >= COLD_RETRY_MS
    }

    fun isDue(userId: String, generatedAt: Long?): Boolean {
        if (generatedAt == null) return canRetryCold(userId)
        val reference = maxOf(generatedAt, lastAttemptAt[userId] ?: Long.MIN_VALUE)
        return clock() - reference >= intervalMs + jitterMs(userId, reference)
    }

    private fun jitterMs(userId: String, reference: Long): Long {
        val span = intervalMs / JITTER_DIVISOR
        if (span <= 0L) return 0L
        return Math.floorMod((userId.hashCode().toLong() * 31L) xor reference, span)
    }

    private companion object {
        const val COLD_RETRY_MS = 60_000L
        const val JITTER_DIVISOR = 4L
    }
}
