package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingTopicGroup

object RecommendationOnboardingCatalog {
    const val MIN_TOPICS = 3

    fun groups(): List<RecommendationOnboardingTopicGroup> = RecommendationOnboardingTopicGroups.all
}
