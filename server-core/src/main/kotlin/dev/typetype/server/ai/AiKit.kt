package dev.typetype.server.ai

interface AiKit {
    val enabled: Boolean
    val providerId: String?

    suspend fun complete(request: AiCompletionRequest): AiCompletionResult
}
