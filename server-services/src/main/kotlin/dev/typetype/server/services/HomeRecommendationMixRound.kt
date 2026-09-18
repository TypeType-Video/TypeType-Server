package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool

object HomeRecommendationMixRound {
    fun pick(
        pool: HomeRecommendationPool,
        machine: HomeRecommendationStateMachine,
        state: HomeRecommendationMixState,
    ): HomeRecommendationSelection? {
        val decision = machine.decide(
            subscriptionCount = state.subscriptionCount,
            discoveryCount = state.discoveryCount,
            noveltyCount = state.noveltyCount,
            selected = state.subscriptionCount + state.discoveryCount,
            subscriptionRun = state.subscriptionRun,
            preferDiscovery = state.preferDiscovery,
        )
        val picker = HomeRecommendationPicker(
            pool = pool,
            channelCount = state.channelCount,
            recentChannels = state.memory.recentChannelsSet(),
            recentSemanticKeys = state.memory.recentSemanticKeysSet(),
            creatorMomentum = state.memory.creatorMomentumMap(),
            creatorCooldownUntilMs = state.memory.creatorCooldownMap(),
            recentTopicPairs = state.memory.recentTopicPairsSet(),
            recentUrls = state.memory.recentUrlsSet(),
        )
        return HomeRecommendationSelector.pick(
            picker = picker,
            wantDiscovery = decision.wantDiscovery,
            forceDiscovery = decision.forceDiscovery,
            forceNovelty = decision.forceNovelty,
            subIndex = state.subIndex,
            discoveryIndex = state.discoveryIndex,
        )
    }
}
