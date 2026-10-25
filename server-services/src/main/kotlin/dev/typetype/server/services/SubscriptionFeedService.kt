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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SubscriptionFeedService(
    subscriptionsService: SubscriptionsService,
    channelService: ChannelService,
    cache: CacheService,
    private val clock: () -> Long = System::currentTimeMillis,
    refreshScope: CoroutineScope? = null,
    channelVideos: ChannelVideosSource = ChannelServiceVideosSource(channelService),
) {
    private val refreshScope = refreshScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ownsRefreshScope = refreshScope == null
    private val store = SubscriptionFeedSnapshotStore(cache, clock)
    private val selections = SubscriptionFeedSelectionStore(cache, subscriptionsService)
    private val refresher = SubscriptionFeedRefresher(subscriptionsService, store, SubscriptionFeedBuilder(channelVideos), clock)
    private val refreshJobs = ConcurrentHashMap<String, Job>()
    private val planner = SubscriptionFeedRefreshPlanner(clock)

    suspend fun getPage(
        userId: String,
        page: Int,
        limit: Int,
        cursor: String?,
        hideLiveStreams: Boolean = false,
        hideMembersOnlyContent: Boolean = false,
        selection: SubscriptionSelection = SubscriptionSelection.All,
        requestId: String? = currentRequestId(),
    ): SubscriptionFeedPageResult {
        planner.touch(userId)
        val current = store.current(userId)
        if (current == null) {
            requestColdRefresh(userId, requestId)
            return SubscriptionFeedPageResult.Preparing(PREPARING_RETRY_AFTER_MS)
        }
        val cursorState = cursor?.let(SubscriptionFeedCursorCodec::decode)
        if (cursor != null && cursorState == null) return SubscriptionFeedPageResult.InvalidCursor
        if (cursorState != null && cursorState.limit != limit) return SubscriptionFeedPageResult.InvalidCursor
        if (cursorState != null && cursorState.hideLiveStreams != hideLiveStreams) {
            return SubscriptionFeedPageResult.InvalidCursor
        }
        if (cursorState != null && cursorState.hideMembersOnlyContent != hideMembersOnlyContent) {
            return SubscriptionFeedPageResult.InvalidCursor
        }
        if (cursorState != null && cursorState.filterKey != selection.cursorKey) {
            return SubscriptionFeedPageResult.InvalidCursor
        }
        val snapshot = when {
            cursorState == null -> current
            cursorState.generation == current.generation -> current
            else -> store.previous(userId)?.takeIf { it.generation == cursorState.generation }
                ?: return SubscriptionFeedPageResult.StaleGeneration
        }
        if (selection != SubscriptionSelection.All && !snapshot.hasCompleteSourceAttribution()) {
            if (cursorState != null) return SubscriptionFeedPageResult.StaleGeneration
            requestColdRefresh(userId, requestId)
            return SubscriptionFeedPageResult.Preparing(PREPARING_RETRY_AFTER_MS)
        }
        val offset = cursorState?.offset ?: page * limit
        val selected = selections.resolve(userId, selection, cursorState?.selectionToken)
            ?: return SubscriptionFeedPageResult.StaleGeneration
        val response = snapshot.page(
            offset,
            limit,
            isRefreshing(userId),
            hideLiveStreams,
            hideMembersOnlyContent,
            selection,
            selected.channelUrls,
            selected.token,
        )
        if (cursorState == null && response.nextpage != null && !selections.persist(userId, selection, selected)) {
            return SubscriptionFeedPageResult.CursorCapacityReached
        }
        return SubscriptionFeedPageResult.Ready(response)
    }

    suspend fun getFeed(userId: String, page: Int, limit: Int): SubscriptionFeedResponse =
        when (val result = getPage(userId, page, limit, cursor = null)) {
            is SubscriptionFeedPageResult.Ready -> result.response
            else -> SubscriptionFeedResponse(emptyList(), null, refreshing = true)
        }

    suspend fun getAll(userId: String): List<VideoItem> {
        return getAllWithAvailability(userId).videos
    }

    suspend fun getAllWithAvailability(userId: String): SubscriptionFeedAvailability {
        planner.touch(userId)
        val snapshot = store.current(userId)
        if (snapshot == null) requestColdRefresh(userId, currentRequestId())
        if (snapshot != null) {
            return SubscriptionFeedAvailability(
                videos = snapshot.videos,
                available = !snapshot.stale,
            )
        }
        withTimeoutOrNull(INTERNAL_COLD_WAIT_MS) { awaitRefresh(userId) }
        val refreshed = store.current(userId)
        return SubscriptionFeedAvailability(
            videos = refreshed?.videos.orEmpty(),
            available = refreshed != null && !refreshed.stale,
        )
    }

    suspend fun getAllWithSources(userId: String): SubscriptionFeedWithSources =
        getAllWithAvailability(userId).let { SubscriptionFeedWithSources(it.videos, it.available, store.current(userId)?.sourceChannelUrls.orEmpty()) }

    suspend fun getCachedFeed(userId: String, page: Int, limit: Int): SubscriptionFeedResponse? {
        val snapshot = store.current(userId) ?: return null
        return snapshot.page(page * limit, limit, isRefreshing(userId))
    }

    suspend fun getCachedAll(userId: String): List<VideoItem>? =
        store.current(userId)?.videos

    suspend fun invalidate(userId: String) {
        runCatching { store.invalidate(userId, UUID.randomUUID().toString()) }
            .onFailure { logger.warn("subscription_feed event=invalidate_failed user={} error={}", userKey(userId), it.message) }
        scheduleRefresh(userId, currentRequestId())
    }

    suspend fun refreshDueUsers() {
        planner.activeUsers().forEach { userId ->
            val generatedAt = store.current(userId)?.generatedAt
            if (!isRefreshing(userId) && planner.isDue(userId, generatedAt)) scheduleRefresh(userId, null)
        }
    }

    suspend fun awaitRefresh(userId: String) {
        while (true) {
            val jobs = listOfNotNull(refreshJobs[userId])
            if (jobs.isEmpty()) return
            jobs.joinAll()
        }
    }

    fun isRefreshing(userId: String): Boolean = refreshJobs[userId]?.isActive == true

    fun close() {
        refreshJobs.values.forEach(Job::cancel)
        refreshJobs.clear()
        if (ownsRefreshScope) refreshScope.cancel()
    }

    private fun requestColdRefresh(userId: String, requestId: String?) {
        if (planner.canRetryCold(userId)) scheduleRefresh(userId, requestId)
    }

    private fun scheduleRefresh(userId: String, requestId: String?) {
        val job = refreshScope.launch(start = CoroutineStart.LAZY) {
            planner.recordAttempt(userId)
            var retry = false
            val invalidation = store.invalidationToken(userId)
            try {
                retry = refresher.refresh(userId, requestId, invalidation)
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

    private fun userKey(userId: String): String = SubscriptionFeedCacheKeys.feed(userId).substringAfter(':')

    companion object {
        private const val PREPARING_RETRY_AFTER_MS = 500L
        private const val INTERNAL_COLD_WAIT_MS = 400L
        private val logger = LoggerFactory.getLogger(SubscriptionFeedService::class.java)
    }
}
