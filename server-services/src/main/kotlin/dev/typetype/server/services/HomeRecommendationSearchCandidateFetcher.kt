package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult

class HomeRecommendationSearchCandidateFetcher(
    private val searchService: SearchService,
    private val trendingService: TrendingService,
) {
    suspend fun fetchTrendingCandidates(serviceId: Int): List<HomeRecommendationTaggedVideo> =
        when (val trending = trendingService.getTrending(serviceId)) {
            is ExtractionResult.Success -> trending.data.map {
                HomeRecommendationTaggedVideo(it, HomeRecommendationSourceTag.DISCOVERY_TRENDING)
            }
            is ExtractionResult.BadRequest -> emptyList()
            is ExtractionResult.Failure -> emptyList()
        }

    suspend fun fetchSearchCandidates(
        serviceId: Int,
        queries: List<String>,
        maxQueries: Int,
        perQueryLimit: Int,
        source: HomeRecommendationSourceTag,
    ): List<HomeRecommendationTaggedVideo> {
        val items = mutableListOf<HomeRecommendationTaggedVideo>()
        queries.take(maxQueries).forEach { query ->
            when (val result = searchService.search(query = query, serviceId = serviceId, nextpage = null)) {
                is ExtractionResult.Success -> items += result.data.items
                    .take(perQueryLimit)
                    .map { HomeRecommendationTaggedVideo(it, source) }
                is ExtractionResult.BadRequest -> Unit
                is ExtractionResult.Failure -> Unit
            }
        }
        return items
    }
}
