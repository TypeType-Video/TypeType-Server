package dev.typetype.server.ai

data class AiCompletionRequest(
    val userPrompt: String,
    val systemPrompt: String? = null,
    val responseFormat: AiResponseFormat = AiResponseFormat.Text,
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    val cacheKey: String? = null,
    val cacheTtlSeconds: Long = DEFAULT_CACHE_TTL_SECONDS,
    val images: List<AiImageInput> = emptyList(),
) {
    companion object {
        const val DEFAULT_CACHE_TTL_SECONDS: Long = 86_400L
    }
}
