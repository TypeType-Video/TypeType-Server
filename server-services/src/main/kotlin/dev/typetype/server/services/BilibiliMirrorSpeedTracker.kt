package dev.typetype.server.services

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

object BilibiliMirrorSpeedTracker {
    data class HostStats(
        var smoothedLatencyMs: Double = 0.0,
        var sampleCount: Int = 0,
        var consecutiveFailures: Int = 0,
        var lastFailureEpochMs: Long = 0L,
        var bytesPerSecond: Double = 0.0,
        var lastThroughputEpochMs: Long = 0L,
    )

    private val stats = ConcurrentHashMap<String, HostStats>()
    private const val PENALTY_DURATION_MS = 120_000L
    private const val PENALTY_SCORE = 15_000.0
    private const val THROUGHPUT_LIFETIME_MS = 600_000L
    private const val MIN_PREFERRED_SPEEDUP = 2.0

    fun recordSuccess(urlOrHost: String, latencyMs: Long) {
        val host = extractHost(urlOrHost) ?: return
        val current = stats.computeIfAbsent(host) { HostStats() }
        synchronized(current) {
            current.consecutiveFailures = 0
            val sample = latencyMs.toDouble().coerceAtLeast(1.0)
            current.smoothedLatencyMs = if (current.sampleCount == 0) {
                sample
            } else {
                current.smoothedLatencyMs * 0.7 + sample * 0.3
            }
            current.sampleCount++
        }
    }

    fun recordThroughput(urlOrHost: String, bytesPerSecond: Double, nowMs: Long = System.currentTimeMillis()) {
        val host = extractHost(urlOrHost) ?: return
        if (bytesPerSecond <= 0.0) return
        val current = stats.computeIfAbsent(host) { HostStats() }
        synchronized(current) {
            val fresh = nowMs - current.lastThroughputEpochMs < THROUGHPUT_LIFETIME_MS && current.bytesPerSecond > 0.0
            current.bytesPerSecond = if (fresh) current.bytesPerSecond * 0.5 + bytesPerSecond * 0.5 else bytesPerSecond
            current.lastThroughputEpochMs = nowMs
        }
    }

    fun preferMeasuredFaster(urls: List<String>, nowMs: Long = System.currentTimeMillis()): List<String> {
        val first = urls.firstOrNull() ?: return urls
        val firstRate = recentThroughput(first, nowMs) ?: return urls
        val best = urls.drop(1).maxByOrNull { recentThroughput(it, nowMs) ?: 0.0 } ?: return urls
        val bestRate = recentThroughput(best, nowMs) ?: return urls
        if (bestRate < firstRate * MIN_PREFERRED_SPEEDUP) return urls
        return listOf(best) + urls.filter { it != best }
    }

    private fun recentThroughput(urlOrHost: String, nowMs: Long): Double? {
        val current = stats[extractHost(urlOrHost) ?: return null] ?: return null
        synchronized(current) {
            if (current.bytesPerSecond <= 0.0 || nowMs - current.lastThroughputEpochMs >= THROUGHPUT_LIFETIME_MS) return null
            return current.bytesPerSecond
        }
    }

    fun recordFailure(urlOrHost: String) {
        val host = extractHost(urlOrHost) ?: return
        val current = stats.computeIfAbsent(host) { HostStats() }
        synchronized(current) {
            current.consecutiveFailures++
            current.lastFailureEpochMs = System.currentTimeMillis()
        }
    }

    private fun regionBucketOffset(host: String, region: String?): Double =
        when (region?.lowercase()) {
            "overseas" -> when {
                isOverseasUposHost(host) -> 0.0
                isDomesticUposHost(host) -> 10_000.0
                else -> 20_000.0
            }
            "domestic" -> when {
                isDomesticUposHost(host) -> 0.0
                isOverseasUposHost(host) -> 10_000.0
                else -> 20_000.0
            }
            else -> 0.0
        }

    fun score(urlOrHost: String, region: String? = null): Double {
        val host = extractHost(urlOrHost) ?: return Double.MAX_VALUE
        val bucketOffset = regionBucketOffset(host, region)
        val current = stats[host]
        val staticIndex = ALL_UPOS_MIRROR_HOSTS.indexOf(host).toDouble() * 100.0
        if (current == null) return bucketOffset + staticIndex

        synchronized(current) {
            val now = System.currentTimeMillis()
            val inPenalty = (now - current.lastFailureEpochMs) < PENALTY_DURATION_MS && current.consecutiveFailures > 0
            val penalty = if (inPenalty) PENALTY_SCORE * current.consecutiveFailures else 0.0
            return bucketOffset + if (current.sampleCount > 0) {
                current.smoothedLatencyMs + penalty
            } else {
                staticIndex + penalty
            }
        }
    }

    fun prioritize(urls: List<String>, region: String?): List<String> {
        if (region == null) return urls
        return urls.sortedBy { score(it, region) }
    }

    fun reset() {
        stats.clear()
    }

    private fun extractHost(urlOrHost: String): String? {
        val host = if (urlOrHost.contains("://")) urlOrHost.toHttpUrlOrNull()?.host else urlOrHost
        return host?.lowercase()?.takeIf { it in ALL_UPOS_MIRROR_HOSTS }
    }
}
