package dev.typetype.server.services

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

object BilibiliMirrorSpeedTracker {
    data class HostStats(
        var smoothedLatencyMs: Double = 0.0,
        var sampleCount: Int = 0,
        var consecutiveFailures: Int = 0,
        var lastFailureEpochMs: Long = 0L,
    )

    private val stats = ConcurrentHashMap<String, HostStats>()
    private const val PENALTY_DURATION_MS = 120_000L // 2 minutes cooldown after failure
    private const val PENALTY_SCORE = 15_000.0 // 15s score penalty per failure

    fun recordSuccess(urlOrHost: String, latencyMs: Long) {
        val host = extractHost(urlOrHost) ?: return
        val current = stats.computeIfAbsent(host) { HostStats() }
        synchronized(current) {
            current.consecutiveFailures = 0
            if (current.sampleCount == 0) {
                current.smoothedLatencyMs = latencyMs.toDouble().coerceAtLeast(1.0)
            } else {
                current.smoothedLatencyMs = current.smoothedLatencyMs * 0.7 + latencyMs.toDouble().coerceAtLeast(1.0) * 0.3
            }
            current.sampleCount++
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

    private fun regionBucketOffset(host: String, region: String?): Double {
        return when (region?.lowercase()) {
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
    }

    fun score(urlOrHost: String, region: String? = null): Double {
        val host = extractHost(urlOrHost) ?: return Double.MAX_VALUE
        val bucketOffset = regionBucketOffset(host, region)
        val current = stats[host]
        val staticIndex = ALL_UPOS_MIRROR_HOSTS.indexOf(host).let { if (it >= 0) it.toDouble() * 100.0 else 5000.0 }
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

    fun prioritize(urls: List<String>, region: String? = null): List<String> =
        urls.sortedBy { score(it, region) }

    fun reset() {
        stats.clear()
    }

    private fun extractHost(urlOrHost: String): String? {
        if (urlOrHost.contains("://")) {
            return urlOrHost.toHttpUrlOrNull()?.host
        }
        return urlOrHost
    }
}
