package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingTopicGroup

object RecommendationOnboardingNewsTopics {
    val group = RecommendationOnboardingTopicGroup(
        id = "news-current-events",
        label = "News & Current Events",
        topics = listOf(
            "News",
            "Politics",
            "World News",
            "Tech News",
            "Sports News",
            "Entertainment News",
            "Business News",
            "Analysis",
            "Commentary",
            "Podcasts",
            "Interviews",
            "Debates",
            "Current Events",
        ),
    )
}
