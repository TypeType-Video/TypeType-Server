package dev.typetype.server.services

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

internal class YoutubeTakeoutImportProgress(private val total: Long) {
    private val updates = Channel<Int>(Channel.UNLIMITED)
    private val published = AtomicInteger(-1)

    fun offer(processed: Long) {
        val percent = if (total <= 0L) 99 else {
            ((processed.toDouble() / total.toDouble()) * 99.0).toInt().coerceIn(0, 99)
        }
        publish(percent)
    }

    fun finish() {
        publish(99)
    }

    fun close() {
        updates.close()
    }

    suspend fun drain(publish: suspend (Int) -> Unit) {
        var applied = -1
        for (value in updates) {
            if (value <= applied) continue
            applied = value
            publish(value)
        }
    }

    private fun publish(percent: Int) {
        while (true) {
            val current = published.get()
            if (percent <= current) return
            if (published.compareAndSet(current, percent)) {
                updates.trySend(percent)
                return
            }
        }
    }
}
