package dev.typetype.server.services

class SabrDemandDeadline(
    private val timeoutMs: Long,
    private val maximumBackoffExtensionMs: Long = SabrPumpPolicy.MAX_DEMAND_BACKOFF_EXTENSION_MS,
) {
    private var identity: String? = null
    private var registeredAtMs = Long.MIN_VALUE
    private var expiresAtMs = Long.MIN_VALUE
    private var coveredBackoffUntilMs = Long.MIN_VALUE
    private var backoffExtensionMs = 0L

    fun isExpired(
        identity: String,
        registeredAtMs: Long,
        nowMs: Long,
        backoffRemainingMs: Long,
    ): Boolean = nowMs >= deadlineAtMs(identity, registeredAtMs, nowMs, backoffRemainingMs)

    fun deadlineAtMs(
        identity: String,
        registeredAtMs: Long,
        nowMs: Long,
        backoffRemainingMs: Long,
    ): Long {
        if (this.identity != identity || this.registeredAtMs != registeredAtMs) {
            reset(identity, registeredAtMs)
        }
        extendForBackoff(nowMs, backoffRemainingMs)
        return expiresAtMs
    }

    private fun reset(identity: String, registeredAtMs: Long) {
        this.identity = identity
        this.registeredAtMs = registeredAtMs
        expiresAtMs = saturatedAdd(registeredAtMs, timeoutMs)
        coveredBackoffUntilMs = Long.MIN_VALUE
        backoffExtensionMs = 0L
    }

    private fun extendForBackoff(nowMs: Long, remainingMs: Long) {
        if (remainingMs <= 0L) return
        val backoffUntilMs = saturatedAdd(nowMs, remainingMs)
        val uncoveredFromMs = maxOf(nowMs, coveredBackoffUntilMs)
        val availableExtensionMs = (maximumBackoffExtensionMs - backoffExtensionMs).coerceAtLeast(0L)
        val uncoveredMs = (backoffUntilMs - uncoveredFromMs).coerceIn(0L, availableExtensionMs)
        expiresAtMs = saturatedAdd(expiresAtMs, uncoveredMs)
        backoffExtensionMs = saturatedAdd(backoffExtensionMs, uncoveredMs)
        coveredBackoffUntilMs = maxOf(coveredBackoffUntilMs, backoffUntilMs)
    }

    private fun saturatedAdd(value: Long, increment: Long): Long =
        if (value >= Long.MAX_VALUE - increment) Long.MAX_VALUE else value + increment
}
