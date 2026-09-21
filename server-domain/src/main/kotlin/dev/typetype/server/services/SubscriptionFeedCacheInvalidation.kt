package dev.typetype.server.services

interface SubscriptionFeedCacheInvalidator {
    suspend fun invalidate(userId: String)
    suspend fun awaitRefresh(userId: String)
}

object SubscriptionFeedCacheInvalidation {
    @Volatile
    private var invalidator: SubscriptionFeedCacheInvalidator? = null

    fun configure(invalidator: SubscriptionFeedCacheInvalidator) {
        this.invalidator = invalidator
    }

    suspend fun invalidate(userId: String) {
        invalidator?.invalidate(userId)
    }

    suspend fun awaitRefresh(userId: String) {
        invalidator?.awaitRefresh(userId)
    }
}
