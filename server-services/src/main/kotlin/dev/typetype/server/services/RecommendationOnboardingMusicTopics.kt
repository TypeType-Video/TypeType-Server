package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingTopicGroup

object RecommendationOnboardingMusicTopics {
    val group = RecommendationOnboardingTopicGroup(
        id = "music",
        label = "Music",
        topics = listOf(
            "Music",
            "Pop Music",
            "Hip Hop",
            "R&B",
            "Rock",
            "Metal",
            "Jazz",
            "Classical",
            "Electronic",
            "EDM",
            "Lo-Fi",
            "K-Pop",
            "J-Pop",
            "Country",
            "Indie Music",
            "Music Production",
            "Guitar",
            "Piano",
            "Singing",
            "Music Theory",
            "Album Reviews",
            "Concerts",
            "DJ",
        ),
    )
}
