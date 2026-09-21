package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
enum class HomeRecommendationSessionPersona {
    AUTO,
    QUICK,
    DEEP,
}
