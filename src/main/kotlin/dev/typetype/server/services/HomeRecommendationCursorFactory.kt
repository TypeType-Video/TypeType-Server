package dev.typetype.server.services

object HomeRecommendationCursorFactory {
    fun nextCursor(
        subIndex: Int,
        discoveryIndex: Int,
        subscriptionRun: Int,
        preferDiscovery: Boolean,
        rotationSeed: Long,
        personaState: HomeRecommendationPersonaState,
        snapshot: HomeRecommendationCursorMemory,
    ): String = HomeRecommendationCursorCodec.encode(
        HomeRecommendationCursor(
            subscriptionIndex = subIndex,
            discoveryIndex = discoveryIndex,
            subscriptionRun = subscriptionRun,
            preferDiscovery = preferDiscovery,
            rotationSeed = rotationSeed,
            recentChannels = snapshot.recentChannels,
            recentSemanticKeys = snapshot.recentSemanticKeys,
            creatorMomentum = snapshot.creatorMomentum,
            creatorCooldownUntilMs = snapshot.creatorCooldownUntilMs,
            recentTopicPairs = snapshot.recentTopicPairs,
            recentUrls = snapshot.recentUrls,
            personaState = personaState,
        ),
    )
}
