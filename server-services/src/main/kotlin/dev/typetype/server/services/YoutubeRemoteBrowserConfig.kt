package dev.typetype.server.services

data class YoutubeRemoteBrowserConfig(
    val serviceUrl: String,
    val callbackBaseUrl: String,
    val internalToken: String?,
    val ttlMs: Long,
    val maxGlobalSessions: Int,
    val maxFrameBytes: Int,
    val maxInputBytes: Int,
    val outboundQueueSize: Int,
) {
    val callbackUrl: String =
        "${callbackBaseUrl.trimEnd('/')}/internal/youtube-remote-login/callback"
    val isConfigured: Boolean = !internalToken.isNullOrBlank()

    companion object {
        private const val DEFAULT_TTL_MS = 8 * 60 * 1000L
        private const val DEFAULT_MAX_GLOBAL_SESSIONS = 2
        private const val DEFAULT_MAX_FRAME_BYTES = 512 * 1024
        private const val DEFAULT_MAX_INPUT_BYTES = 4096
        private const val DEFAULT_OUTBOUND_QUEUE_SIZE = 2

        fun fromEnvironment(tokenServiceUrl: String): YoutubeRemoteBrowserConfig =
            YoutubeRemoteBrowserConfig(
                serviceUrl = envText("YOUTUBE_REMOTE_LOGIN_SERVICE_URL") ?: tokenServiceUrl,
                callbackBaseUrl = envText("YOUTUBE_REMOTE_LOGIN_CALLBACK_BASE_URL") ?: "http://localhost:8080",
                internalToken = SecretConfigReader.read("YOUTUBE_REMOTE_LOGIN_INTERNAL_TOKEN"),
                ttlMs = envLong("YOUTUBE_REMOTE_LOGIN_TTL_MS", DEFAULT_TTL_MS).coerceIn(60_000L, 10 * 60_000L),
                maxGlobalSessions = envInt("YOUTUBE_REMOTE_LOGIN_MAX_SESSIONS", DEFAULT_MAX_GLOBAL_SESSIONS).coerceIn(1, 8),
                maxFrameBytes = envInt("YOUTUBE_REMOTE_LOGIN_MAX_FRAME_BYTES", DEFAULT_MAX_FRAME_BYTES).coerceIn(64 * 1024, 2 * 1024 * 1024),
                maxInputBytes = envInt("YOUTUBE_REMOTE_LOGIN_MAX_INPUT_BYTES", DEFAULT_MAX_INPUT_BYTES).coerceIn(512, 16 * 1024),
                outboundQueueSize = envInt("YOUTUBE_REMOTE_LOGIN_OUTBOUND_QUEUE_SIZE", DEFAULT_OUTBOUND_QUEUE_SIZE).coerceIn(1, 8),
            )

        private fun envText(name: String): String? =
            System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

        private fun envLong(name: String, fallback: Long): Long =
            envText(name)?.toLongOrNull() ?: fallback

        private fun envInt(name: String, fallback: Int): Int =
            envText(name)?.toIntOrNull() ?: fallback
    }
}
