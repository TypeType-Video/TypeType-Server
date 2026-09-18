package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool

object HomeRecommendationShortsRefresher {
    fun refresh(
        pool: HomeRecommendationPool,
        page: HomeRecommendationPage,
        cursor: HomeRecommendationCursor,
    ): HomeRecommendationShortsRefreshResult {
        val minDiscovery = minOf((page.items.size * page.discoveryFloorRatio).toInt(), page.items.size)
        if (page.discoveryCount >= minDiscovery) return HomeRecommendationShortsRefreshResult(pool, null)
        val recovered = pool.subscriptions
            .drop(HomeRecommendationShortsSources.DISCOVERY_RECOVERY_DROP)
            .take(HomeRecommendationShortsSources.DISCOVERY_RECOVERY_TAKE)
        if (recovered.isEmpty() && page.discoveryCount > 0) {
            return HomeRecommendationShortsRefreshResult(pool, rewindCursor(cursor))
        }
        if (recovered.isEmpty()) return HomeRecommendationShortsRefreshResult(pool, null)
        val mergedDiscovery = (pool.discovery + recovered).distinctBy { it.url }
        val nextCursor = rewindCursor(cursor)
        val mergedSourceByUrl = pool.sourceByUrl.toMutableMap()
        recovered.forEach { mergedSourceByUrl[it.url] = HomeRecommendationSourceTag.DISCOVERY_EXPLORATION }
        return HomeRecommendationShortsRefreshResult(
            pool = pool.copy(discovery = mergedDiscovery, sourceByUrl = mergedSourceByUrl),
            cursorOverride = nextCursor,
        )
    }

    private fun rewindCursor(cursor: HomeRecommendationCursor): HomeRecommendationCursor {
        val rewind = HomeRecommendationShortsSources.DISCOVERY_CURSOR_REWIND
        val discoveryIndex = (cursor.discoveryIndex - rewind).coerceAtLeast(0)
        return cursor.copy(discoveryIndex = discoveryIndex)
    }
}

data class HomeRecommendationShortsRefreshResult(
    val pool: HomeRecommendationPool,
    val cursorOverride: HomeRecommendationCursor?,
)
