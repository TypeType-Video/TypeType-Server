package dev.typetype.server.services

data class HomeRecommendationEngagementSignals(
    val videoPenalty: Map<String, Double>,
    val implicitBlockedVideos: Set<String>,
    val rejectionTopicPenalty: Map<String, Double> = emptyMap(),
    val rejectionChannelPenalty: Map<String, Double> = emptyMap(),
    val rejectionTopicPairPenalty: Map<String, Double> = emptyMap(),
)
