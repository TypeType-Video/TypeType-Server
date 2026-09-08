package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeRecommendationWarmupService(
    private val recommendationService: HomeRecommendationService,
    private val cache: CacheService,
) : HomeRecommendationWarmup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracker = HomeWarmupTracker(WARMUP_THROTTLE_MS, ACTIVE_TTL_MS)
    private val poolCache = HomeRecommendationPoolCache(cache)

    init {
        scope.launch { refreshLoop() }
    }

    override fun markActive(userId: String) {
        tracker.markActive(userId, System.currentTimeMillis())
        schedule(userId)
    }

    override fun invalidateAndWarm(userId: String) {
        tracker.markActive(userId, System.currentTimeMillis())
        scope.launch {
            invalidate(userId)
            SubscriptionFeedCacheInvalidation.awaitRefresh(userId)
            schedule(userId, force = true)
        }
    }

    private fun schedule(userId: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!tracker.trySchedule(userId, now, force)) return
        scope.launch { warm(userId) }
    }

    private suspend fun warm(userId: String) {
        val context = context()
        runCatching {
            recommendationService.getHome(userId, YOUTUBE_SERVICE_ID, WARMUP_LIMIT, HomeRecommendationCursor(), context)
        }
        runCatching {
            recommendationService.getShorts(userId, YOUTUBE_SERVICE_ID, WARMUP_LIMIT, HomeRecommendationCursor(), context)
        }
    }

    private suspend fun invalidate(userId: String) {
        SubscriptionFeedCacheInvalidation.invalidate(userId)
        poolCache.delete(userId, YOUTUBE_SERVICE_ID, HomeRecommendationPoolMode.FULL)
        poolCache.delete(userId, YOUTUBE_SERVICE_ID, HomeRecommendationPoolMode.SHORTS)
    }

    private suspend fun refreshLoop() {
        while (scope.isActive) {
            delay(REFRESH_INTERVAL_MS)
            val now = System.currentTimeMillis()
            tracker.activeUsers(now).forEach { schedule(it) }
        }
    }

    private fun context(): HomeRecommendationContext = HomeRecommendationContext(
        serviceId = YOUTUBE_SERVICE_ID,
        sessionContext = HomeRecommendationSessionContext(
            intent = HomeRecommendationSessionIntent.AUTO,
            deviceClass = HomeRecommendationDeviceClass.UNKNOWN,
        ),
    )

    companion object {
        private const val WARMUP_LIMIT = 20
        private const val WARMUP_THROTTLE_MS = 10 * 60 * 1000L
        private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
        private const val ACTIVE_TTL_MS = 60 * 60 * 1000L
    }
}
