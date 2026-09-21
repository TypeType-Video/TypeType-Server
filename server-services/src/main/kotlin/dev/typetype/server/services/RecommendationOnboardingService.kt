package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingPreferencesRequest
import dev.typetype.server.models.RecommendationOnboardingStateResponse
import dev.typetype.server.models.RecommendationOnboardingTopicsResponse

class RecommendationOnboardingService(
    private val stateStore: RecommendationOnboardingStateStore = RecommendationOnboardingStateStore(),
    private val preferencesStore: RecommendationOnboardingPreferencesStore = RecommendationOnboardingPreferencesStore(),
    private val seedService: RecommendationOnboardingSeedService = RecommendationOnboardingSeedService(),
) {
    suspend fun topics(): RecommendationOnboardingTopicsResponse = RecommendationOnboardingTopicsResponse(
        minTopics = RecommendationOnboardingCatalog.MIN_TOPICS,
        groups = RecommendationOnboardingCatalog.groups(),
    )

    suspend fun state(userId: String): RecommendationOnboardingStateResponse {
        val completedAt = stateStore.readCompletedAt(userId)
        val selectedTopics = preferencesStore.readTopics(userId)
        val selectedChannels = preferencesStore.readChannels(userId)
        return RecommendationOnboardingStateResponse(
            requiresOnboarding = completedAt == null,
            completedAt = completedAt,
            selectedTopics = selectedTopics,
            selectedChannels = selectedChannels,
        )
    }

    suspend fun savePreferences(userId: String, request: RecommendationOnboardingPreferencesRequest): RecommendationOnboardingStateResponse {
        val topics = normalizeTopics(request.selectedTopics)
        val channels = request.selectedChannels.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(60)
        preferencesStore.save(userId = userId, topics = topics, channels = channels)
        return state(userId)
    }

    suspend fun complete(userId: String): RecommendationOnboardingStateResponse {
        val saved = state(userId)
        require(saved.selectedTopics.size >= RecommendationOnboardingCatalog.MIN_TOPICS)
        val now = System.currentTimeMillis()
        stateStore.markCompleted(userId = userId, at = now)
        seedService.apply(userId = userId, topics = saved.selectedTopics, channels = saved.selectedChannels)
        return state(userId)
    }

    suspend fun skip(userId: String): RecommendationOnboardingStateResponse {
        val now = System.currentTimeMillis()
        stateStore.markCompleted(userId = userId, at = now)
        return state(userId)
    }

    suspend fun reapply(userId: String): RecommendationOnboardingStateResponse {
        val saved = state(userId)
        require(saved.completedAt != null)
        seedService.apply(userId = userId, topics = saved.selectedTopics, channels = saved.selectedChannels)
        return saved
    }

    private fun normalizeTopics(raw: List<String>): List<String> = raw
        .map { RecommendationTopicTokenizer.tokenize(it).firstOrNull().orEmpty() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(40)
}
