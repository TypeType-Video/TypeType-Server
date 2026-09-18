package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

object HomeRecommendationMixEngine {
    fun mix(
        pool: HomeRecommendationPool,
        cursor: HomeRecommendationCursor,
        limit: Int,
        context: HomeRecommendationSessionContext,
        sourceWeights: Map<HomeRecommendationSourceTag, Double>,
        mode: HomeRecommendationPoolMode,
        userId: String?,
        serviceId: Int?,
    ): HomeRecommendationPage {
        val planner = HomeRecommendationQuotaPlanner(
            limit = limit,
            subscriptionSize = pool.subscriptions.size,
            discoverySize = pool.discovery.size,
            sourceByUrl = pool.sourceByUrl,
            sourceWeights = sourceWeights,
            sessionContext = context,
            personaState = cursor.personaState,
            mode = mode,
        )
        val selected = mutableListOf<VideoItem>()
        val sourceBySelectedUrl = linkedMapOf<String, HomeRecommendationSourceTag>()
        val state = HomeRecommendationMixState(cursor, context)
        val machine = HomeRecommendationStateMachine(planner)
        while (selected.size < limit) {
            val pick = HomeRecommendationMixRound.pick(pool, machine, state)
            if (pick == null) break
            selected += pick.video
            sourceBySelectedUrl[pick.video.url] = pick.source
            state.onSelected(
                video = pick.video,
                state = pick.state,
                source = pick.source,
                isNovelty = pick.isNovelty,
            )
        }
        val nextCursor = HomeRecommendationNextCursor.create(pool, selected, state)
        HomeRecommendationShortsAudit.tryLog(
            mode = mode,
            selectedSize = selected.size,
            userId = userId,
            serviceId = serviceId,
            context = context,
            discoveryCount = state.discoveryCount,
            subscriptionCount = state.subscriptionCount,
            targetDiscoveryRatio = planner.target.targetDiscoveryRatio,
            discoveryFloorRatio = planner.target.discoveryFloorRatio,
            sourceBySelectedUrl = sourceBySelectedUrl,
        )
        return HomeRecommendationPage(
            items = selected,
            nextCursor = nextCursor,
            subscriptionCount = state.subscriptionCount,
            discoveryCount = state.discoveryCount,
            targetDiscoveryRatio = planner.target.targetDiscoveryRatio,
            discoveryFloorRatio = planner.target.discoveryFloorRatio,
            sourceByUrl = sourceBySelectedUrl,
        )
    }
}
