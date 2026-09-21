package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
data class HomeRecommendationPersonaState(
    val persona: HomeRecommendationSessionPersona = HomeRecommendationSessionPersona.AUTO,
    val quickEvidence: Int = 0,
    val deepEvidence: Int = 0,
)
