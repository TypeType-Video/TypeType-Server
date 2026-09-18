package dev.typetype.server.services

data class RecommendationFeedbackSignals(
    val blockedVideos: Set<String>,
    val blockedUploaders: Set<String>,
)

class RecommendationFeedbackSignalService(private val feedbackService: RecommendationFeedbackService) {
    suspend fun load(userId: String): RecommendationFeedbackSignals {
        val items = runCatching { feedbackService.getAll(userId) }.getOrElse { emptyList() }
        val blockedVideos = items
            .asSequence()
            .filter { it.feedbackType == "not_interested" }
            .mapNotNull { it.videoUrl }
            .toSet()
        val blockedUploaders = items
            .asSequence()
            .filter { it.feedbackType == "less_from_channel" }
            .mapNotNull { it.uploaderUrl }
            .toSet()
        return RecommendationFeedbackSignals(blockedVideos = blockedVideos, blockedUploaders = blockedUploaders)
    }
}
