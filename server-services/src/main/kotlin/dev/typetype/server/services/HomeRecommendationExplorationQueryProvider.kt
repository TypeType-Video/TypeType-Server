package dev.typetype.server.services

object HomeRecommendationExplorationQueryProvider {
    fun queries(mode: HomeRecommendationPoolMode): List<String> {
        val limit = if (mode == HomeRecommendationPoolMode.FAST) FAST_QUERY_COUNT else FULL_QUERY_COUNT
        return DEFAULT_QUERIES.take(limit)
    }

    fun shortQueries(): List<String> = SHORT_QUERIES

    private val DEFAULT_QUERIES = listOf(
        "trending videos",
        "viral videos",
        "new uploads",
        "breaking news",
        "technology",
        "gaming",
        "music",
        "documentary",
    )

    private val SHORT_QUERIES = listOf(
        "shorts trending",
        "viral shorts",
        "quick facts shorts",
        "short comedy",
        "short tech clips",
        "music shorts",
    )

    private const val FAST_QUERY_COUNT = 2
    private const val FULL_QUERY_COUNT = 6
}
