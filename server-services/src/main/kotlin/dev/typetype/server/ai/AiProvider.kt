package dev.typetype.server.ai

interface AiProvider {
    val id: String
    val model: String

    suspend fun complete(request: AiCompletionRequest): AiCompletionResult
}
