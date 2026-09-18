package dev.typetype.server.services

import dev.typetype.server.cache.CacheService

class SubscriptionFeedCacheInvalidatorImpl(
    private val cache: CacheService,
    private val feedService: SubscriptionFeedService,
) : SubscriptionFeedCacheInvalidator {
    override suspend fun invalidate(userId: String) {
        feedService.invalidate(userId)
        runCatching { cache.delete(SubscriptionFeedCacheKeys.shorts(userId)) }
    }

    override suspend fun awaitRefresh(userId: String) {
        feedService.awaitRefresh(userId)
    }
}
