package dev.typetype.server.services

class RecommendationInterestService(
    private val batchUpdater: RecommendationInterestBatchUpdater = RecommendationInterestBatchUpdater(),
) {
    suspend fun update(userId: String, eventType: String, uploaderUrl: String?, title: String?, watchRatio: Double?) {
        val weight = RecommendationInterestWeight.of(eventType, watchRatio)
        if (weight == 0.0) return
        batchUpdater.apply(
            userId = userId,
            uploaderUrl = uploaderUrl,
            title = title,
            delta = weight,
            now = System.currentTimeMillis(),
        )
    }
}
