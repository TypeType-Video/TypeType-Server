package dev.typetype.server.services

object HomeRecommendationShortsFallback {
    fun apply(pool: HomeRecommendationCandidatePool): HomeRecommendationCandidatePool {
        if (pool.discovery.isNotEmpty() || pool.subscriptions.size >= HomeRecommendationShortsSources.MIN_SUBSCRIPTION_POOL) return pool
        val base = if (pool.subscriptions.size <= HomeRecommendationShortsSources.DISCOVERY_RECOVERY_DROP) {
            pool.subscriptions
        } else {
            pool.subscriptions.drop(HomeRecommendationShortsSources.DISCOVERY_RECOVERY_DROP)
        }
        val recovered = base
            .take(HomeRecommendationShortsSources.DISCOVERY_RECOVERY_TAKE)
            .map { it.copy(source = HomeRecommendationSourceTag.DISCOVERY_EXPLORATION) }
        if (recovered.isEmpty()) return pool
        return HomeRecommendationCandidatePool(
            subscriptions = pool.subscriptions,
            discovery = (pool.discovery + recovered).distinctBy { it.video.url },
        )
    }
}
