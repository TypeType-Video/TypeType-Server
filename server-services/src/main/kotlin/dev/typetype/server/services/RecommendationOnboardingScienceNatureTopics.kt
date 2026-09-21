package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingTopicGroup

object RecommendationOnboardingScienceNatureTopics {
    val group = RecommendationOnboardingTopicGroup(
        id = "science-nature",
        label = "Science & Nature",
        topics = listOf(
            "Space",
            "Astronomy",
            "NASA",
            "Physics",
            "Nature",
            "Animals",
            "Wildlife",
            "Ocean",
            "Marine Life",
            "Environment",
            "Climate",
            "Geology",
            "Paleontology",
            "Dinosaurs",
            "Engineering",
            "Inventions",
            "Experiments",
        ),
    )
}
