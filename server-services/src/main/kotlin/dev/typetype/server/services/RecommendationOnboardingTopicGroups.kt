package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingTopicGroup

object RecommendationOnboardingTopicGroups {
    val all: List<RecommendationOnboardingTopicGroup> = listOf(
        RecommendationOnboardingGamingTopics.group,
        RecommendationOnboardingMusicTopics.group,
        RecommendationOnboardingTechnologyTopics.group,
        RecommendationOnboardingEntertainmentTopics.group,
        RecommendationOnboardingEducationTopics.group,
        RecommendationOnboardingHealthFitnessTopics.group,
        RecommendationOnboardingLifestyleTopics.group,
        RecommendationOnboardingCreativeTopics.group,
        RecommendationOnboardingScienceNatureTopics.group,
        RecommendationOnboardingNewsTopics.group,
    )
}
