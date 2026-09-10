package dev.typetype.server.services

internal class HomeWarmupTracker(
    private val throttleMs: Long,
    private val activeTtlMs: Long,
) {
    private val active = mutableMapOf<String, Long>()
    private val started = mutableMapOf<String, Long>()

    @Synchronized
    fun markActive(userId: String, now: Long) {
        active[userId] = now
    }

    @Synchronized
    fun trySchedule(userId: String, now: Long, force: Boolean): Boolean {
        val previous = started[userId]
        if (!force && previous != null && now - previous < throttleMs) return false
        started[userId] = now
        return true
    }

    @Synchronized
    fun activeUsers(now: Long): List<String> {
        active.entries.removeIf { now - it.value > activeTtlMs }
        started.keys.retainAll(active.keys)
        return active.keys.toList()
    }
}
