package dev.typetype.server.services

class HomeRecommendationMomentumMemory(cursor: HomeRecommendationCursor) {
    private val recentChannels = ArrayDeque(cursor.recentChannels.takeLast(MAX_RECENT_CHANNEL_MEMORY))
    private val recentSemanticKeys = ArrayDeque(cursor.recentSemanticKeys.takeLast(MAX_RECENT_SEMANTIC_MEMORY))
    private val topicPairWindow = ArrayDeque(cursor.recentTopicPairs.takeLast(MAX_RECENT_TOPIC_PAIR_MEMORY))
    private val recentUrls = ArrayDeque(cursor.recentUrls.takeLast(MAX_RECENT_URL_MEMORY))
    private val creatorMomentum = cursor.creatorMomentum.toMutableMap()
    private val creatorCooldown = cursor.creatorCooldownUntilMs.toMutableMap()

    fun recentChannelsSet(): Set<String> = recentChannels.toSet()

    fun recentSemanticKeysSet(): Set<String> = recentSemanticKeys.toSet()

    fun creatorMomentumMap(): Map<String, Int> = creatorMomentum

    fun creatorCooldownMap(): Map<String, Long> = creatorCooldown

    fun recentTopicPairsSet(): Set<String> = topicPairWindow.toSet()

    fun recentUrlsSet(): Set<String> = recentUrls.toSet()

    fun onSelected(videoTitle: String, uploaderKey: String) {
        if (uploaderKey.isNotBlank()) {
            recentChannels += uploaderKey
            while (recentChannels.size > MAX_RECENT_CHANNEL_MEMORY) recentChannels.removeFirst()
            creatorMomentum[uploaderKey] = ((creatorMomentum[uploaderKey] ?: 0) + 1).coerceAtMost(MAX_CREATOR_MOMENTUM)
            if ((creatorMomentum[uploaderKey] ?: 0) >= CREATOR_COOLDOWN_THRESHOLD) {
                creatorCooldown[uploaderKey] = System.currentTimeMillis() + CREATOR_COOLDOWN_MS
                creatorMomentum[uploaderKey] = CREATOR_COOLDOWN_THRESHOLD - 1
            }
        }
        val semanticKey = HomeRecommendationSemanticKey.fromTitle(videoTitle)
        if (semanticKey.isNotBlank()) {
            recentSemanticKeys += semanticKey
            while (recentSemanticKeys.size > MAX_RECENT_SEMANTIC_MEMORY) recentSemanticKeys.removeFirst()
        }
        HomeRecommendationTopicPairs.fromTitle(videoTitle).forEach { pair ->
            topicPairWindow += pair
            while (topicPairWindow.size > MAX_RECENT_TOPIC_PAIR_MEMORY) topicPairWindow.removeFirst()
        }
    }

    fun onSelectedUrl(url: String) {
        if (url.isBlank()) return
        recentUrls += url
        while (recentUrls.size > MAX_RECENT_URL_MEMORY) recentUrls.removeFirst()
    }

    fun snapshot(): HomeRecommendationCursorMemory = HomeRecommendationCursorMemory(
        recentChannels = recentChannels.toList(),
        recentSemanticKeys = recentSemanticKeys.toList(),
        creatorMomentum = creatorMomentum,
        creatorCooldownUntilMs = creatorCooldown,
        recentTopicPairs = topicPairWindow.toList(),
        recentUrls = recentUrls.toList(),
    )

    companion object {
        private const val MAX_RECENT_CHANNEL_MEMORY = 4
        private const val MAX_RECENT_SEMANTIC_MEMORY = 5
        private const val MAX_RECENT_TOPIC_PAIR_MEMORY = 14
        private const val MAX_RECENT_URL_MEMORY = 80
        private const val MAX_CREATOR_MOMENTUM = 6
        private const val CREATOR_COOLDOWN_THRESHOLD = 3
        private const val CREATOR_COOLDOWN_MS = 45L * 60L * 1000L
    }
}
