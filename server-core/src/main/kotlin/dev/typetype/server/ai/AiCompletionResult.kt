package dev.typetype.server.ai

sealed interface AiCompletionResult {
    data class Success(
        val text: String,
        val model: String,
        val providerId: String,
        val cached: Boolean = false,
    ) : AiCompletionResult

    data object Disabled : AiCompletionResult

    data class RateLimited(
        val reason: String,
    ) : AiCompletionResult

    data class Failed(
        val reason: String,
        val retryable: Boolean = false,
    ) : AiCompletionResult
}
