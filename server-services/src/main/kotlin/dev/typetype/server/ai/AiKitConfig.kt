package dev.typetype.server.ai

data class AiKitConfig(
    val provider: String? = null,
    val outboundProxyUrl: String? = null,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val requestsPerMinute: Int = DEFAULT_REQUESTS_PER_MINUTE,
    val dailyRequestLimit: Int = DEFAULT_DAILY_REQUEST_LIMIT,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 15_000L
        const val MIN_TIMEOUT_MS: Long = 1_000L
        const val MAX_TIMEOUT_MS: Long = 120_000L
        const val DEFAULT_REQUESTS_PER_MINUTE: Int = 15
        const val DEFAULT_DAILY_REQUEST_LIMIT: Int = 200

        fun fromEnvironment(env: (String) -> String? = System::getenv): AiKitConfig = AiKitConfig(
            provider = env("AI_PROVIDER")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            outboundProxyUrl = env("AI_OUTBOUND_PROXY_URL")?.trim()?.takeIf { it.isNotEmpty() },
            timeoutMs = env("AI_TIMEOUT_MS")
                ?.trim()
                ?.toLongOrNull()
                ?.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
                ?: DEFAULT_TIMEOUT_MS,
            requestsPerMinute = env("AI_REQUESTS_PER_MINUTE")
                ?.trim()
                ?.toIntOrNull()
                ?: DEFAULT_REQUESTS_PER_MINUTE,
            dailyRequestLimit = env("AI_DAILY_REQUEST_LIMIT")
                ?.trim()
                ?.toIntOrNull()
                ?: DEFAULT_DAILY_REQUEST_LIMIT,
        )
    }
}
