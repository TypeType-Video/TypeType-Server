package dev.typetype.server.services

import dev.typetype.server.models.RecommendationEventItem

object HomeRecommendationPersonaTracker {
    fun infer(
        events: List<RecommendationEventItem>,
        sessionContext: HomeRecommendationSessionContext,
    ): HomeRecommendationPersonaState {
        val base = when (sessionContext.intent) {
            HomeRecommendationSessionIntent.QUICK -> HomeRecommendationPersonaState(HomeRecommendationSessionPersona.QUICK, quickEvidence = 1)
            HomeRecommendationSessionIntent.DEEP -> HomeRecommendationPersonaState(HomeRecommendationSessionPersona.DEEP, deepEvidence = 1)
            HomeRecommendationSessionIntent.AUTO -> HomeRecommendationPersonaState()
        }
        if (events.isEmpty()) return base
        val scoped = events.take(60)
        val quickSignals = scoped.count { it.eventType == "short_skip" || ((it.watchDurationMs ?: 0L) in 1..90_000L) }
        val deepSignals = scoped.count {
            (it.eventType == "watch" && (it.watchRatio ?: 0.0) >= 0.65) || ((it.watchDurationMs ?: 0L) >= 240_000L)
        }
        val quickEvidence = (base.quickEvidence + quickSignals).coerceAtMost(8)
        val deepEvidence = (base.deepEvidence + deepSignals).coerceAtMost(8)
        val persona = when {
            deepEvidence - quickEvidence >= 2 -> HomeRecommendationSessionPersona.DEEP
            quickEvidence - deepEvidence >= 2 -> HomeRecommendationSessionPersona.QUICK
            else -> base.persona
        }
        return HomeRecommendationPersonaState(persona = persona, quickEvidence = quickEvidence, deepEvidence = deepEvidence)
    }
}
