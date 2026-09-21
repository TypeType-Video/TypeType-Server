package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingTopicGroup

object RecommendationOnboardingEntertainmentTopics {
    val group = RecommendationOnboardingTopicGroup(
        id = "entertainment",
        label = "Entertainment",
        topics = listOf(
            "Movies",
            "TV Shows",
            "Netflix",
            "Anime",
            "Marvel",
            "DC",
            "Star Wars",
            "Disney",
            "Comedy",
            "Stand-up Comedy",
            "Drama",
            "Horror",
            "Sci-Fi",
            "Documentary",
            "Film Analysis",
            "Movie Reviews",
            "Behind the Scenes",
            "Celebrities",
            "Award Shows",
            "Trailers",
            "Fan Theories",
        ),
    )
}
