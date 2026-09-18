package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingTopicGroup

object RecommendationOnboardingCreativeTopics {
    val group = RecommendationOnboardingTopicGroup(
        id = "creative",
        label = "Creative",
        topics = listOf(
            "Art",
            "Drawing",
            "Painting",
            "Digital Art",
            "Animation",
            "3D Modeling",
            "Graphic Design",
            "Video Editing",
            "Filmmaking",
            "Photography",
            "Music Production",
            "Writing",
            "Storytelling",
            "Architecture",
            "Fashion Design",
            "Crafts",
            "Woodworking",
            "Sculpture",
        ),
    )
}
