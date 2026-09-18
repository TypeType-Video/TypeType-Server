package dev.typetype.server.services

data class AuthSessionConfig(
    val refreshTtlDays: Long = DEFAULT_REFRESH_TTL_DAYS,
    val allowInsecureCookies: Boolean = false,
) {
    val refreshTtlMs: Long = refreshTtlDays * MILLIS_PER_DAY
    val refreshTtlSeconds: Long = refreshTtlDays * SECONDS_PER_DAY

    companion object {
        const val DEFAULT_REFRESH_TTL_DAYS = 30L
        const val MIN_REFRESH_TTL_DAYS = 1L
        const val MAX_REFRESH_TTL_DAYS = 365L
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val SECONDS_PER_DAY = 24L * 60L * 60L

        fun fromEnvironment(read: (String) -> String? = System::getenv): AuthSessionConfig =
            AuthSessionConfig(
                refreshTtlDays = read("AUTH_SESSION_TTL_DAYS")
                    ?.trim()
                    ?.toLongOrNull()
                    ?.coerceIn(MIN_REFRESH_TTL_DAYS, MAX_REFRESH_TTL_DAYS)
                    ?: DEFAULT_REFRESH_TTL_DAYS,
                allowInsecureCookies = read("AUTH_ALLOW_INSECURE_COOKIES").isEnabled(),
            )

        private fun String?.isEnabled(): Boolean = when (this?.trim()?.lowercase()) {
            "1", "true", "yes", "on" -> true
            else -> false
        }
    }
}
