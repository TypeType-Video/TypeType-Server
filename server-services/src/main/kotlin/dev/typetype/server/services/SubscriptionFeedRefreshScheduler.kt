package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class SubscriptionFeedRefreshScheduler(
    private val feedService: SubscriptionFeedService,
    private val tickMs: Long = 60_000L,
    private val initialDelayMs: Long = 15_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(initialDelayMs)
            while (isActive) {
                try {
                    feedService.refreshDueUsers()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    logger.warn("subscription_feed event=refresh_tick_failed error={}", error.message)
                }
                delay(tickMs)
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SubscriptionFeedRefreshScheduler::class.java)
    }
}
