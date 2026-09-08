package dev.typetype.server.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PushNotificationScheduler(
    private val service: PushNotificationService,
    private val intervalMs: Long = configuredIntervalMs(),
    private val initialDelayMs: Long = 10_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(initialDelayMs)
            while (isActive) {
                service.pollRegisteredAccounts()
                delay(intervalMs)
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        fun configuredIntervalMs(): Long =
            (System.getenv("TYPE_TYPE_PUSH_NOTIFICATIONS_INTERVAL_SECONDS")?.toLongOrNull() ?: 300L)
                .coerceIn(30L, 86_400L) * 1_000L
    }
}
