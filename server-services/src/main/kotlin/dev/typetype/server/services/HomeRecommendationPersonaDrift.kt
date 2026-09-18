package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationPersonaDrift {
    fun seed(context: HomeRecommendationSessionContext, state: HomeRecommendationPersonaState): HomeRecommendationPersonaState {
        return when (context.intent) {
            HomeRecommendationSessionIntent.QUICK -> HomeRecommendationPersonaState(
                persona = HomeRecommendationSessionPersona.QUICK,
                quickEvidence = state.quickEvidence.coerceAtLeast(1),
                deepEvidence = state.deepEvidence,
            )
            HomeRecommendationSessionIntent.DEEP -> HomeRecommendationPersonaState(
                persona = HomeRecommendationSessionPersona.DEEP,
                quickEvidence = state.quickEvidence,
                deepEvidence = state.deepEvidence.coerceAtLeast(1),
            )
            HomeRecommendationSessionIntent.AUTO -> state
        }
    }

    fun onSelected(state: HomeRecommendationPersonaState, video: VideoItem): HomeRecommendationPersonaState {
        val duration = video.duration
        val quickDelta = if (duration in 30..420) 1 else 0
        val deepDelta = if (duration >= 1_200) 1 else 0
        if (quickDelta == 0 && deepDelta == 0) return state
        val quickEvidence = (state.quickEvidence + quickDelta).coerceAtMost(8)
        val deepEvidence = (state.deepEvidence + deepDelta).coerceAtMost(8)
        val persona = when {
            deepEvidence - quickEvidence >= 2 -> HomeRecommendationSessionPersona.DEEP
            quickEvidence - deepEvidence >= 2 -> HomeRecommendationSessionPersona.QUICK
            else -> HomeRecommendationSessionPersona.AUTO
        }
        return HomeRecommendationPersonaState(persona = persona, quickEvidence = quickEvidence, deepEvidence = deepEvidence)
    }
}
