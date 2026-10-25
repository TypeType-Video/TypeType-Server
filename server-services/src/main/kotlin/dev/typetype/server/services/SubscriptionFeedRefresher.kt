package dev.typetype.server.services

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

class SubscriptionFeedRefresher(
    private val subscriptionsService: SubscriptionsService,
    private val store: SubscriptionFeedSnapshotStore,
    private val builder: SubscriptionFeedBuilder,
    private val clock: () -> Long,
) {
    private val orderer = SubscriptionFeedOrderer()
    private val generation = AtomicLong(clock())

    suspend fun refresh(userId: String, requestId: String?, invalidation: String?): Boolean {
        val startedAt = clock()
        val previous = store.current(userId)
        logger.info("subscription_feed event=refresh_started user={} requestId={}", userKey(userId), requestId ?: "none")
        val subscriptions = subscriptionsService.getAll(userId)
        val result = builder.build(subscriptions, previous)
        if (store.invalidationToken(userId) != invalidation) return true
        val valid = result.successfulSources > 0 || subscriptions.isEmpty()
        if (!valid) {
            if (previous != null) store.markStale(userId)
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

    private companion object {
        val logger = LoggerFactory.getLogger(SubscriptionFeedRefresher::class.java)

        fun userKey(userId: String): String = SubscriptionFeedCacheKeys.feed(userId).substringAfter(':')
    }
}
