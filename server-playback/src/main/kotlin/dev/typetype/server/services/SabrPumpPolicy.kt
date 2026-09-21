package dev.typetype.server.services

object SabrPumpPolicy {
    const val IDLE_POLL_MS = 100L
    const val ERROR_RETRY_MS = 1_000L
    const val MAX_CONSECUTIVE_IO_ERRORS = 5
    const val READAHEAD_CUSHION_MS = 10_000L
    const val STARTUP_READAHEAD_CUSHION_MS = 6_000L
    const val STARTUP_BURST_READAHEAD_CUSHION_MS = 25_000L
    const val STARTUP_BURST_MS = 25_000L
    const val SEEK_READAHEAD_CUSHION_MS = 5_000L
    const val SEEK_MODE_MS = 8_000L
    const val MIN_SERVER_READAHEAD_CUSHION_MS = 3_000L
    const val SERVER_AHEAD_MARGIN_MS = 16_000L
    const val DEMAND_TARGET_DEADLINE_MS = 15_000L
    const val MAX_DEMAND_BACKOFF_EXTENSION_MS = 30_000L
    const val MAX_PROTECTED_NO_MEDIA_RESPONSES = 5
    const val COMPLETED_DEMAND_IDLE_MS = 1_000L
    const val MAX_AHEAD_BYTES = 24L * 1024L * 1024L
    const val BACK_BUFFER_MS = 12_000L
    const val MIN_BACK_BUFFER_MS = 2_000L
    const val BACK_BUFFER_BYTES = 4L * 1024L * 1024L

    fun demandDelayMs(intervalMs: Long, backoffMs: Long, activeLive: Boolean, futureDemand: Boolean?): Long =
        maxOf(
            intervalMs,
            backoffMs,
            LIVE_EDGE_POLL_MS.takeIf { futureDemand != null || activeLive } ?: 0L,
        )

    fun backBufferMs(holder: SabrSessionHolder): Long {
        val cachedBytes = runCatchingNonCancellation { holder.session.cachedBytes }.getOrDefault(0L)
        if (cachedBytes > MAX_AHEAD_BYTES) return MIN_BACK_BUFFER_MS
        val bitsPerSecond = holder.videoFormat.bitrate.toLong() + holder.audioFormat.bitrate.coerceAtLeast(0).toLong()
        if (bitsPerSecond <= 0L) return BACK_BUFFER_MS
        val bytesPerMs = (bitsPerSecond / 8L / 1_000L).coerceAtLeast(1L)
        return (BACK_BUFFER_BYTES / bytesPerMs).coerceIn(MIN_BACK_BUFFER_MS, BACK_BUFFER_MS)
    }
}
