package dev.typetype.server.ai

import dev.typetype.server.services.SecretConfigReader

data class GeminiConfig(
    val apiKey: String?,
    val model: String = DEFAULT_MODEL,
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    val enabled: Boolean
        get() = !apiKey.isNullOrBlank()

    companion object {
        const val DEFAULT_MODEL: String = "gemini-3.5-flash-lite"
        const val DEFAULT_BASE_URL: String = "https://generativelanguage.googleapis.com"

        fun fromEnvironment(
            env: (String) -> String? = System::getenv,
            secret: (String) -> String? = SecretConfigReader::read,
        ): GeminiConfig {
            val apiKey = secret("AI_GEMINI_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }
            val model = env("AI_GEMINI_MODEL")?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL
            val baseUrl = env("AI_GEMINI_BASE_URL")
                ?.trim()
                ?.trimEnd('/')
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_BASE_URL
            return GeminiConfig(
                apiKey = apiKey,
                model = model,
                baseUrl = baseUrl,
            )
        }
    }
}
