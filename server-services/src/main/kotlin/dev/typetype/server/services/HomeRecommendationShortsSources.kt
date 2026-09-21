package dev.typetype.server.services

object HomeRecommendationShortsSources {
    const val SUBSCRIPTION_LIMIT = 90
    const val SEARCH_QUERY_LIMIT = 8
    const val SEARCH_PER_QUERY_LIMIT = 14
    const val DISCOVERY_CAP = 180
    const val DISCOVERY_REBALANCE_LIMIT = 160
    const val MIN_DISCOVERY_POOL = 40
    const val WATCH_LATER_SEED_LIMIT = 8
    const val MIN_SUBSCRIPTION_POOL = 6
    const val DISCOVERY_RECOVERY_DROP = 4
    const val DISCOVERY_RECOVERY_TAKE = 24
    const val TARGET_DISCOVERY_RATIO = 0.60
    const val TARGET_DISCOVERY_RATIO_MAX = 0.82
    const val FLOOR_DISCOVERY_RATIO_AUTO = 0.65
    const val FLOOR_DISCOVERY_RATIO_DEEP = 0.60
    const val DISCOVERY_CURSOR_REWIND = 24
}
