package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationOnboardingTopicGroup(
    val id: String,
    val label: String,
    val topics: List<String>,
)

@Serializable
data class RecommendationOnboardingTopicsResponse(
    val minTopics: Int,
    val groups: List<RecommendationOnboardingTopicGroup>,
)

@Serializable
data class RecommendationOnboardingStateResponse(
    val requiresOnboarding: Boolean,
    val completedAt: Long? = null,
    val selectedTopics: List<String> = emptyList(),
    val selectedChannels: List<String> = emptyList(),
)

@Serializable
data class RecommendationOnboardingPreferencesRequest(
    val selectedTopics: List<String> = emptyList(),
    val selectedChannels: List<String> = emptyList(),
)
