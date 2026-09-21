package dev.typetype.server.services

class HomeRecommendationStateMachine(private val planner: HomeRecommendationQuotaPlanner) {
    fun decide(
        subscriptionCount: Int,
        discoveryCount: Int,
        noveltyCount: Int,
        selected: Int,
        subscriptionRun: Int,
        preferDiscovery: Boolean,
    ): HomeRecommendationDecision {
        val force = planner.shouldForceDiscovery(
            subscriptionCount = subscriptionCount,
            discoveryCount = discoveryCount,
            selected = selected,
        )
        val prefer = planner.shouldPreferDiscovery(
            subscriptionRun = subscriptionRun,
            discoveryCount = discoveryCount,
            preferDiscovery = preferDiscovery,
        ) || force
        return HomeRecommendationDecision(
            forceDiscovery = force,
            wantDiscovery = prefer,
            target = planner.target,
            forceNovelty = planner.shouldForceNovelty(noveltyCount = noveltyCount, selected = selected),
        )
    }
}
