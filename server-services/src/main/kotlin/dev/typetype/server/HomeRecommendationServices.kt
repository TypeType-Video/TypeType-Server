package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.HomeRecommendationPoolResolverDependencies
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.HomeRecommendationWarmupService

data class HomeRecommendationServices(
    val recommendationService: HomeRecommendationService,
    val warmupService: HomeRecommendationWarmupService,
) : AutoCloseable {
    override fun close() {
        warmupService.close()
        recommendationService.close()
    }
}

fun createHomeRecommendationServices(
    cache: DragonflyService,
    deps: HomeRecommendationPoolResolverDependencies,
): HomeRecommendationServices {
    val resolverDeps = deps.copy(cache = cache)
    val recommendationService = HomeRecommendationService(
        poolResolver = HomeRecommendationPoolResolver(resolverDeps),
    )
    val warmupService = HomeRecommendationWarmupService(recommendationService, cache)
    return HomeRecommendationServices(recommendationService, warmupService)
}
