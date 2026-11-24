package dev.typetype.server.services

data class FocusAiConfig(
    val enabled: Boolean = false,
    val passScore: Int = DEFAULT_PASS_SCORE,
    val maxCooldownMinutes: Int = DEFAULT_MAX_COOLDOWN_MINUTES,
    val rejectionCooldownMinutes: Int = DEFAULT_REJECTION_COOLDOWN_MINUTES,
) {
    companion object {
        const val DEFAULT_PASS_SCORE = 80
        const val DEFAULT_MAX_COOLDOWN_MINUTES = 60
        const val DEFAULT_REJECTION_COOLDOWN_MINUTES = 60
        private const val MIN_SCORE = 1
        private const val MAX_SCORE = 99
        private const val MIN_MINUTES = 1
        private const val MAX_MINUTES = 180

        fun fromEnvironment(env: (String) -> String? = System::getenv): FocusAiConfig = FocusAiConfig(
            enabled = env("FOCUS_AI_ENABLED")?.trim()?.equals("true", ignoreCase = true) == true,
            passScore = env("FOCUS_AI_PASS_SCORE")?.trim()?.toIntOrNull()?.coerceIn(MIN_SCORE, MAX_SCORE)
                ?: DEFAULT_PASS_SCORE,
            maxCooldownMinutes = env("FOCUS_AI_MAX_COOLDOWN_MINUTES")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(MIN_MINUTES, MAX_MINUTES)
                ?: DEFAULT_MAX_COOLDOWN_MINUTES,
            rejectionCooldownMinutes = env("FOCUS_AI_REJECTION_COOLDOWN_MINUTES")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(MIN_MINUTES, MAX_MINUTES)
                ?: DEFAULT_REJECTION_COOLDOWN_MINUTES,
        )
    }
}
