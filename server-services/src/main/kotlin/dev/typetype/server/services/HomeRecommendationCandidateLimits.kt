package dev.typetype.server.services

object HomeRecommendationCandidateLimits {
    const val FAST_SUBSCRIPTION_PAGE_SIZE = 60
    const val SUBSCRIPTION_SEED_LIMIT = 8
    const val FAVORITE_SEED_LIMIT = 6
    const val RELATED_PER_SEED_LIMIT = 10
    const val RELATED_DISCOVERY_CAP = 48
    const val FAST_THEME_QUERY_LIMIT = 2
    const val FULL_THEME_QUERY_LIMIT = 6
    const val SIGNAL_QUERY_LIMIT = 4
    const val EXPLORATION_QUERY_LIMIT = 6
    const val THEME_SEARCH_PER_QUERY = 18
    const val SIGNAL_SEARCH_PER_QUERY = 14
    const val EXPLORATION_SEARCH_PER_QUERY = 12
    const val FAST_EXPLORATION_CAP = 24
    const val FULL_EXPLORATION_CAP = 64
}
