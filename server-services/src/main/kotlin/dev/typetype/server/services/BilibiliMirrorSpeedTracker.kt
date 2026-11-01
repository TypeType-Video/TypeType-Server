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
    private const val PENALTY_DURATION_MS = 120_000L
    private const val PENALTY_SCORE = 15_000.0

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
