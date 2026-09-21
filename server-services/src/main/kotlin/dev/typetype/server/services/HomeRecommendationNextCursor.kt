package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

object HomeRecommendationNextCursor {
    fun create(
        pool: HomeRecommendationPool,
        selected: List<VideoItem>,
        state: HomeRecommendationMixState,
    ): String? {
        val hasMore = state.subIndex < pool.subscriptions.size || state.discoveryIndex < pool.discovery.size
        if (!hasMore || selected.isEmpty()) return null
        val snapshot = state.memory.snapshot()
        return HomeRecommendationCursorFactory.nextCursor(
            subIndex = state.subIndex,
            discoveryIndex = state.discoveryIndex,
            subscriptionRun = state.subscriptionRun,
            preferDiscovery = state.preferDiscovery,
            rotationSeed = state.rotationSeed,
            personaState = state.personaState,
            snapshot = snapshot,
        )
    }
}
