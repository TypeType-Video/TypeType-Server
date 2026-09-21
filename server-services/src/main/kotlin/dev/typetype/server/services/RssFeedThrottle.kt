package dev.typetype.server.services

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class RssFeedThrottle(private val clock: () -> Long = System::currentTimeMillis) {
    private val windows = ConcurrentHashMap<String, Window>()
    private val acquisitions = AtomicInteger()

    fun acquire(feedId: String, limit: Int): Int? {
        val now = clock()
        if (acquisitions.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            windows.entries.removeIf { now - it.value.startedAt >= RETENTION_MS }
        }
        val window = windows.compute(feedId) { _, current ->
            if (current == null || now - current.startedAt >= WINDOW_MS) Window(now, 1) else current.copy(count = current.count + 1)
        }!!
        if (window.count <= limit) return null
        return ((WINDOW_MS - (now - window.startedAt) + 999L) / 1_000L).coerceAtLeast(1L).toInt()
    }

    private data class Window(val startedAt: Long, val count: Int)

    companion object {
        private const val WINDOW_MS = 60_000L
        private const val RETENTION_MS = WINDOW_MS * 2
        private const val CLEANUP_INTERVAL = 256
    }
}
