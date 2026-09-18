package dev.typetype.server.services

interface HomeRecommendationWarmup {
    fun markActive(userId: String)
    fun invalidateAndWarm(userId: String)
}

object NoopHomeRecommendationWarmup : HomeRecommendationWarmup {
    override fun markActive(userId: String) = Unit
    override fun invalidateAndWarm(userId: String) = Unit
}
