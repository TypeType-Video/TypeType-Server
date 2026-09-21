package dev.typetype.server.services

class HomeRecommendationQuotaPlanner(
    private val limit: Int,
    private val subscriptionSize: Int,
    private val discoverySize: Int,
    private val sourceByUrl: Map<String, HomeRecommendationSourceTag>,
    private val sourceWeights: Map<HomeRecommendationSourceTag, Double> = emptyMap(),
    private val sessionContext: HomeRecommendationSessionContext,
    private val personaState: HomeRecommendationPersonaState = HomeRecommendationPersonaState(),
    private val mode: HomeRecommendationPoolMode = HomeRecommendationPoolMode.FULL,
) {
    val target = computeTarget()

    fun shouldForceNovelty(noveltyCount: Int, selected: Int): Boolean {
        val remainingSlots = limit - selected
        val remainingNeed = (target.noveltyBudget - noveltyCount).coerceAtLeast(0)
        return remainingNeed >= remainingSlots
    }

    fun shouldForceDiscovery(subscriptionCount: Int, discoveryCount: Int, selected: Int): Boolean {
        val remainingSlots = limit - selected
        val floorNeed = (target.discoveryFloor - discoveryCount).coerceAtLeast(0)
        if (floorNeed >= remainingSlots) return true
        val remainingNeed = (target.targetDiscovery - discoveryCount).coerceAtLeast(0)
        return remainingNeed >= remainingSlots || subscriptionCount >= target.targetSubscription
    }

    fun shouldPreferDiscovery(subscriptionRun: Int, discoveryCount: Int, preferDiscovery: Boolean): Boolean {
        if (subscriptionRun >= MAX_SUBSCRIPTION_RUN) return true
        if (discoveryCount < target.targetDiscovery) return true
        return preferDiscovery
    }

    private fun computeTarget(): HomeRecommendationTargetPlan {
        val themeWeight = sourceByUrl.values.count { it == HomeRecommendationSourceTag.DISCOVERY_THEME }
        val explorationWeight = sourceByUrl.values.count { it == HomeRecommendationSourceTag.DISCOVERY_EXPLORATION }
        val trendingWeight = sourceByUrl.values.count { it == HomeRecommendationSourceTag.DISCOVERY_TRENDING }
        val discoveryWeights = sourceWeights
            .filterKeys { it != HomeRecommendationSourceTag.SUBSCRIPTION }
            .values
        val banditDiscoveryBoost = if (discoveryWeights.isEmpty()) 1.0 else discoveryWeights.average().coerceIn(0.5, 1.4)
        val banditSubscriptionBoost = (sourceWeights[HomeRecommendationSourceTag.SUBSCRIPTION] ?: 1.0).coerceIn(0.5, 1.4)
        val dynamicRatio = when {
            discoverySize == 0 -> 0.0
            subscriptionSize == 0 -> 1.0
            themeWeight + explorationWeight + trendingWeight >= subscriptionSize -> 0.65
            themeWeight + explorationWeight >= subscriptionSize / 2 -> 0.58
            else -> 0.50
        } * (banditDiscoveryBoost / banditSubscriptionBoost)
        val personaBias = when (personaState.persona) {
            HomeRecommendationSessionPersona.AUTO -> 0.0
            HomeRecommendationSessionPersona.QUICK -> 0.06
            HomeRecommendationSessionPersona.DEEP -> -0.06
        }
        val intentAdjustedRatio = when (sessionContext.intent) {
            HomeRecommendationSessionIntent.AUTO -> dynamicRatio
            HomeRecommendationSessionIntent.QUICK -> dynamicRatio + 0.08
            HomeRecommendationSessionIntent.DEEP -> dynamicRatio - 0.08
        } + personaBias
        val ratioBounds = if (mode == HomeRecommendationPoolMode.SHORTS) {
            HomeRecommendationShortsSources.TARGET_DISCOVERY_RATIO..HomeRecommendationShortsSources.TARGET_DISCOVERY_RATIO_MAX
        } else {
            0.30..0.80
        }
        val clampedRatio = intentAdjustedRatio.coerceIn(ratioBounds.start, ratioBounds.endInclusive)
        val targetDiscovery = minOf((limit * clampedRatio).toInt().coerceAtLeast(0), discoverySize)
        val targetSubscription = minOf(limit - targetDiscovery, subscriptionSize)
        val floorRatio = if (mode == HomeRecommendationPoolMode.SHORTS) {
            if (sessionContext.intent == HomeRecommendationSessionIntent.DEEP) {
                HomeRecommendationShortsSources.FLOOR_DISCOVERY_RATIO_DEEP
            } else {
                HomeRecommendationShortsSources.FLOOR_DISCOVERY_RATIO_AUTO
            }
        } else {
            0.50
        }
        val discoveryFloor = minOf((limit * floorRatio).toInt().coerceAtLeast(0), discoverySize)
        val noveltyRatio = when (sessionContext.intent) {
            HomeRecommendationSessionIntent.AUTO -> 0.25
            HomeRecommendationSessionIntent.QUICK -> 0.35
            HomeRecommendationSessionIntent.DEEP -> 0.20
        }
        val noveltyBudget = (limit * noveltyRatio).toInt().coerceIn(1, limit)
        return HomeRecommendationTargetPlan(
            targetSubscription = targetSubscription,
            targetDiscovery = targetDiscovery,
            noveltyBudget = noveltyBudget,
            discoveryFloor = discoveryFloor,
            targetDiscoveryRatio = clampedRatio,
            discoveryFloorRatio = floorRatio,
        )
    }

    companion object {
        const val MAX_SUBSCRIPTION_RUN = 2
    }
}
