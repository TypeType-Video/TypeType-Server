package dev.typetype.server.services

data class TwitchConfig(
    val serviceUrl: String?,
    val serviceSecret: String?,
    val tokenKey: String?,
    val requestTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    val enabled: Boolean
        get() = !serviceUrl.isNullOrBlank() && !serviceSecret.isNullOrBlank() && (tokenKey?.length ?: 0) >= MIN_KEY_LENGTH

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val MIN_KEY_LENGTH = 32

        fun fromEnvironment(
            env: (String) -> String? = System::getenv,
            secret: (String) -> String? = SecretConfigReader::read,
        ): TwitchConfig = TwitchConfig(
            serviceUrl = env("TWITCH_SERVICE_URL")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() },
            serviceSecret = secret("TWITCH_SERVICE_SECRET"),
            tokenKey = secret("TWITCH_TOKEN_ENCRYPTION_KEY"),
            requestTimeoutMs = env("TWITCH_SERVICE_TIMEOUT_MS")?.toLongOrNull() ?: DEFAULT_TIMEOUT_MS,
        )
    }
}
