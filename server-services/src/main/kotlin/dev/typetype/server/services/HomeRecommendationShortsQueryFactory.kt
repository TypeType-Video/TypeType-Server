package dev.typetype.server.services

object HomeRecommendationShortsQueryFactory {
    fun fromProfile(profile: HomeRecommendationProfile, limit: Int = 6): List<String> {
        val theme = profile.themeQueries.take(3)
        val keywords = profile.keywordAffinity
            .filterNot { it in GENERIC_KEYWORDS }
            .take(5)
            .map { "$it shorts" }
        return (theme + keywords + HomeRecommendationExplorationQueryProvider.shortQueries())
            .filter { it.isNotBlank() }
            .distinct()
            .take(limit)
    }

    private val GENERIC_KEYWORDS = setOf("shorts", "random", "facts", "viral", "trending")
}
