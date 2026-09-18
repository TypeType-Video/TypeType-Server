package dev.typetype.server.services

class RecommendationOnboardingSeedService(
    private val interestBatchUpdater: RecommendationInterestBatchUpdater = RecommendationInterestBatchUpdater(),
) {
    suspend fun apply(userId: String, topics: List<String>, channels: List<String>) {
        val now = System.currentTimeMillis()
        topics.forEach { topic ->
            interestBatchUpdater.apply(
                userId = userId,
                uploaderUrl = null,
                title = topic,
                delta = 2.4,
                now = now,
            )
        }
        channels.forEach { channel ->
            interestBatchUpdater.apply(
                userId = userId,
                uploaderUrl = channel,
                title = null,
                delta = 2.6,
                now = now,
            )
        }
    }
}
