package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingTopicGroup

object RecommendationOnboardingEducationTopics {
    val group = RecommendationOnboardingTopicGroup(
        id = "education",
        label = "Education",
        topics = listOf(
            "Science",
            "Physics",
            "Chemistry",
            "Biology",
            "Mathematics",
            "History",
            "Geography",
            "Psychology",
            "Philosophy",
            "Economics",
            "Finance",
            "Investing",
            "Business",
            "Marketing",
            "Language Learning",
            "English",
            "Spanish",
            "Study Tips",
            "College",
            "University",
            "Tutorials",
        ),
    )
}
