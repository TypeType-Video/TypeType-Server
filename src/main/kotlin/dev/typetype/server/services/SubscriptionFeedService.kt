package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.currentRequestId
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SubscriptionFeedService(
    private val subscriptionsService: SubscriptionsService,
    channelService: ChannelService,
    cache: CacheService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val refreshScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val store = SubscriptionFeedSnapshotStore(cache, clock)
    private val builder = SubscriptionFeedBuilder(channelService)
    private val orderer = SubscriptionFeedOrderer()
    private val refreshJobs = ConcurrentHashMap<String, Job>()
    private val generation = AtomicLong(clock())

    internal suspend fun getPage(
        userId: String,
        page: Int,
        limit: Int,
        cursor: String?,
        selection: SubscriptionSelection = SubscriptionSelection.All,
        requestId: String? = currentRequestId(),
    ): SubscriptionFeedPageResult {
        val current = store.current(userId)
        if (current == null) {
            scheduleRefresh(userId, requestId)
            return SubscriptionFeedPageResult.Preparing(PREPARING_RETRY_AFTER_MS)
        }
        if (current.stale || clock() - current.generatedAt >= FRESHNESS_MS) {
            scheduleRefresh(userId, requestId)
        }
        val cursorState = cursor?.let(SubscriptionFeedCursorCodec::decode)
        if (cursor != null && cursorState == null) return SubscriptionFeedPageResult.InvalidCursor
        if (cursorState != null && cursorState.limit != limit) return SubscriptionFeedPageResult.InvalidCursor
        if (cursorState != null && cursorState.filterKey != selection.cursorKey) {
            return SubscriptionFeedPageResult.InvalidCursor
        }
        val snapshot = when {
            cursorState == null -> current
            cursorState.generation == current.generation -> current
            else -> store.previous(userId)?.takeIf { it.generation == cursorState.generation }
                ?: return SubscriptionFeedPageResult.StaleGeneration
        }
        val offset = cursorState?.offset ?: page * limit
        val selectedChannelUrls = if (selection == SubscriptionSelection.All) null else {
            subscriptionsService.getChannelUrls(userId, selection)
        }
        return SubscriptionFeedPageResult.Ready(
            snapshot.page(offset, limit, isRefreshing(userId), selection, selectedChannelUrls),
        )
    }

    suspend fun getFeed(userId: String, page: Int, limit: Int): SubscriptionFeedResponse =
        when (val result = getPage(userId, page, limit, cursor = null)) {
            is SubscriptionFeedPageResult.Ready -> result.response
            else -> SubscriptionFeedResponse(emptyList(), null, refreshing = true)
        }

    suspend fun getAll(userId: String): List<VideoItem> {
        val snapshot = store.current(userId)
        if (snapshot == null || snapshot.stale || clock() - snapshot.generatedAt >= FRESHNESS_MS) {
            scheduleRefresh(userId, currentRequestId())
        }
        if (snapshot != null) return snapshot.videos
        withTimeoutOrNull(INTERNAL_COLD_WAIT_MS) { awaitRefresh(userId) }
        return store.current(userId)?.videos.orEmpty()
    }

    suspend fun getCachedFeed(userId: String, page: Int, limit: Int): SubscriptionFeedResponse? {
        val snapshot = store.current(userId) ?: return null
        return snapshot.page(page * limit, limit, isRefreshing(userId))
    }

    suspend fun invalidate(userId: String) {
        runCatching { store.invalidate(userId, UUID.randomUUID().toString()) }
            .onFailure { logger.warn("subscription_feed event=invalidate_failed user={} error={}", userKey(userId), it.message) }
        scheduleRefresh(userId, currentRequestId())
    }

    internal suspend fun awaitRefresh(userId: String) {
        while (true) {
            val jobs = listOfNotNull(refreshJobs[userId])
            if (jobs.isEmpty()) return
            jobs.joinAll()
        }
    }

    internal fun isRefreshing(userId: String): Boolean = refreshJobs[userId]?.isActive == true

    private fun scheduleRefresh(userId: String, requestId: String?) {
        val job = refreshScope.launch(start = CoroutineStart.LAZY) {
            var retry = false
            val invalidation = store.invalidationToken(userId)
            try {
                retry = refresh(userId, requestId, invalidation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.warn("subscription_feed event=refresh_failed user={} error={}", userKey(userId), error.message)
            } finally {
                coroutineContext[Job]?.let { refreshJobs.remove(userId, it) }
            }
            if (retry || store.invalidationToken(userId) != invalidation) scheduleRefresh(userId, requestId)
        }
        val existing = refreshJobs.putIfAbsent(userId, job)
        if (existing == null) job.start() else job.cancel()
    }

    private suspend fun refresh(userId: String, requestId: String?, invalidation: String?): Boolean {
        val startedAt = clock()
        val previous = store.current(userId)
        logger.info("subscription_feed event=refresh_started user={} requestId={}", userKey(userId), requestId ?: "none")
        val subscriptions = subscriptionsService.getAll(userId)
        val result = builder.build(subscriptions)
        if (store.invalidationToken(userId) != invalidation) return true
        val valid = result.successfulSources > 0 || subscriptions.isEmpty()
        if (!valid) {
            logger.warn(
                "subscription_feed event=refresh_kept_previous user={} durationMs={} failedSources={}",
                userKey(userId), clock() - startedAt, result.failedSources,
            )
            return false
        }
        val refreshedAt = clock()
        val ordering = orderer.order(result.videos, previous, refreshedAt)
        val nextGeneration = generation.updateAndGet { maxOf(it + 1, clock(), (previous?.generation ?: 0L) + 1) }
        val snapshot = SubscriptionFeedSnapshot(
            generation = nextGeneration,
            generatedAt = refreshedAt,
            stale = false,
            videos = ordering.videos,
            livePromotedAt = ordering.livePromotedAt,
            sourceChannelUrls = result.sourceChannelUrls,
        )
        runCatching { store.publish(userId, snapshot) }.onFailure {
            logger.warn("subscription_feed event=publish_failed user={} error={}", userKey(userId), it.message)
            return false
        }
        if (store.invalidationToken(userId) != invalidation) {
            runCatching { store.markStale(userId) }
            return true
        }
        logger.info(
            "subscription_feed event=refresh_completed user={} requestId={} generation={} videos={} failedSources={} durationMs={}",
            userKey(userId), requestId ?: "none", nextGeneration, ordering.videos.size, result.failedSources, clock() - startedAt,
        )
        return false
    }

    private fun userKey(userId: String): String = SubscriptionFeedCacheKeys.feed(userId).substringAfter(':')

    companion object {
        private const val FRESHNESS_MS = 60_000L
        private const val PREPARING_RETRY_AFTER_MS = 500L
        private const val INTERNAL_COLD_WAIT_MS = 400L
        private val logger = LoggerFactory.getLogger(SubscriptionFeedService::class.java)
    }
}
