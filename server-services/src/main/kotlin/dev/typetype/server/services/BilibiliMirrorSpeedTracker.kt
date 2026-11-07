package dev.typetype.server.services

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

object BilibiliMirrorSpeedTracker {
    data class HostStats(
        var smoothedLatencyMs: Double = 0.0,
        var sampleCount: Int = 0,
        var consecutiveFailures: Int = 0,
        var lastFailureEpochMs: Long = 0L,
        var lastSampleEpochMs: Long = 0L,
        var bytesPerSecond: Double = 0.0,
        var lastThroughputEpochMs: Long = 0L,
        var lastLatencyEpochMs: Long = 0L,
    )

    private val stats = ConcurrentHashMap<String, HostStats>()
    const val PENALTY_DURATION_MS = 120_000L
    const val FAST_THROUGHPUT_THRESHOLD = 2.5 * 1024 * 1024.0
    const val FRESHNESS_WINDOW_MS = 180_000L
    private const val PENALTY_SCORE = 15_000.0
    private const val STALE_SAMPLE_DURATION_MS = 90_000L
    private const val THROUGHPUT_LIFETIME_MS = 30 * 60_000L
    private const val TYPICAL_SEGMENT_BYTES = 512.0 * 1024
    private const val UNKNOWN_LATENCY_MS = 600.0
    private const val UNKNOWN_BYTES_PER_SECOND = 400.0 * 1024

    fun recordSuccess(urlOrHost: String, latencyMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val host = extractHost(urlOrHost) ?: return
        val current = stats.computeIfAbsent(host) { HostStats() }
        synchronized(current) {
            current.consecutiveFailures = 0
            current.lastSampleEpochMs = nowMs
            current.lastLatencyEpochMs = nowMs
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

    fun rankByExpectedTime(urls: List<String>, nowMs: Long = System.currentTimeMillis()): List<String> =
        urls.withIndex()
            .sortedWith(compareBy({ expectedSegmentMillis(it.value, nowMs) }, { it.index }))
            .map { it.value }

    fun expectedSegmentMillis(urlOrHost: String, nowMs: Long = System.currentTimeMillis()): Double {
        val host = extractHost(urlOrHost) ?: return Double.MAX_VALUE
        val current = stats[host] ?: return UNKNOWN_LATENCY_MS + TYPICAL_SEGMENT_BYTES * 1000 / UNKNOWN_BYTES_PER_SECOND
        synchronized(current) {
            val latency = current.smoothedLatencyMs
                .takeIf { current.sampleCount > 0 && nowMs - current.lastLatencyEpochMs < THROUGHPUT_LIFETIME_MS }
                ?: UNKNOWN_LATENCY_MS
            val rate = current.bytesPerSecond
                .takeIf { it > 0.0 && nowMs - current.lastThroughputEpochMs < THROUGHPUT_LIFETIME_MS }
                ?: UNKNOWN_BYTES_PER_SECOND
            val inPenalty = current.consecutiveFailures > 0 && nowMs - current.lastFailureEpochMs < penaltyDuration(current.consecutiveFailures)
            val penalty = if (inPenalty) PENALTY_SCORE * current.consecutiveFailures else 0.0
            return latency + TYPICAL_SEGMENT_BYTES * 1000 / rate + penalty
        }
    }

    fun recordFailure(urlOrHost: String, nowMs: Long = System.currentTimeMillis()) {
        val host = extractHost(urlOrHost) ?: return
        val current = stats.computeIfAbsent(host) { HostStats() }
        synchronized(current) {
            current.consecutiveFailures++
            current.lastFailureEpochMs = nowMs
        }
    }

    fun penaltyDuration(failures: Int): Long =
        if (failures <= 0) 0L else PENALTY_DURATION_MS * (1L shl (failures - 1).coerceIn(0, 4))

    fun hasFreshMeasurement(
        urlOrHost: String,
        maxAgeMs: Long = FRESHNESS_WINDOW_MS,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val host = extractHost(urlOrHost) ?: return false
        val current = stats[host] ?: return false
        synchronized(current) {
            val freshThroughput = current.bytesPerSecond > 0.0 && (nowMs - current.lastThroughputEpochMs) < maxAgeMs
            val freshLatency = current.sampleCount > 0 && (nowMs - current.lastLatencyEpochMs) < maxAgeMs
            return freshThroughput || freshLatency
        }
    }

    fun isFastHost(
        urlOrHost: String,
        threshold: Double = FAST_THROUGHPUT_THRESHOLD,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val host = extractHost(urlOrHost) ?: return false
        val current = stats[host] ?: return false
        synchronized(current) {
            return current.bytesPerSecond >= threshold && (nowMs - current.lastThroughputEpochMs) < THROUGHPUT_LIFETIME_MS
        }
    }

    fun findProbeCandidate(urls: List<String>, activeUrl: String, nowMs: Long = System.currentTimeMillis()): String? {
        val activeHost = extractHost(activeUrl)
        return urls.firstOrNull { candidate ->
            val host = extractHost(candidate) ?: return@firstOrNull false
            if (host == activeHost) return@firstOrNull false
            val current = stats[host] ?: return@firstOrNull true
            synchronized(current) {
                val inPenalty = current.consecutiveFailures > 0 && (nowMs - current.lastFailureEpochMs) < penaltyDuration(current.consecutiveFailures)
                val isStale = (nowMs - current.lastThroughputEpochMs) >= THROUGHPUT_LIFETIME_MS
                !inPenalty && isStale
            }
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

    fun score(urlOrHost: String, region: String? = null, nowMs: Long = System.currentTimeMillis()): Double {
        val host = extractHost(urlOrHost) ?: return Double.MAX_VALUE
        val bucketOffset = regionBucketOffset(host, region)
        val current = stats[host]
        val staticIndex = ALL_UPOS_MIRROR_HOSTS.indexOf(host).let { if (it >= 0) it.toDouble() * 1.0 else 5_000.0 }
        if (current == null) return bucketOffset + staticIndex

        synchronized(current) {
            val inPenalty = (nowMs - current.lastFailureEpochMs) < penaltyDuration(current.consecutiveFailures) && current.consecutiveFailures > 0
            val penalty = if (inPenalty) PENALTY_SCORE * current.consecutiveFailures else 0.0
            val isStale = (nowMs - current.lastSampleEpochMs) > STALE_SAMPLE_DURATION_MS
            return bucketOffset + if (current.sampleCount > 0 && !isStale) {
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
        return host?.lowercase()?.takeIf { isUposHost(it) }
    }
}
