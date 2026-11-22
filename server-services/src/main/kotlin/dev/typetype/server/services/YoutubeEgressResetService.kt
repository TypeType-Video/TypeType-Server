package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.security.MessageDigest

@Serializable
data class YoutubeEgressResetRequest(
    val reason: String? = null,
)

@Serializable
data class YoutubeEgressResetResponse(
    val clearedFailureEntries: Int,
    val clearedBackoffKeys: Int,
    val tokenSessionRecoveryTriggered: Boolean,
)

sealed interface YoutubeEgressResetResult {
    data class Completed(val response: YoutubeEgressResetResponse) : YoutubeEgressResetResult
    data class Throttled(val retryAfterSeconds: Long) : YoutubeEgressResetResult
}

/**
 * Resets YouTube-only backoff caches, failure memory, idle egress connections, and token session
 * state under a cooldown when an external egress change (such as a proxy or VPN rotation) completes.
 */
class YoutubeEgressResetService(
    private val cache: CacheService,
    private val clearFailureMemory: () -> Int,
    private val evictConnections: () -> Unit,
    private val recoverTokenSession: (reason: String) -> Boolean,
    private val tokenProvider: () -> String? = { SecretConfigReader.read(TOKEN_ENV) },
    private val restMs: Long = DEFAULT_REST_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var lastResetAtMs: Long? = null

    fun isAuthorized(providedToken: String?): Boolean {
        val expected = tokenProvider()?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val actual = providedToken?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
    }

    suspend fun reset(reason: String = DEFAULT_REASON): YoutubeEgressResetResult {
        val retryAfterSeconds = tryAcquireReset()
        if (retryAfterSeconds > 0L) {
            logger.debug("youtube_egress event=reset_throttled retryAfterSeconds={}", retryAfterSeconds)
            return YoutubeEgressResetResult.Throttled(retryAfterSeconds)
        }
        val normalizedReason = reason.trim().ifEmpty { DEFAULT_REASON }
        val clearedFailureEntries = clearFailureMemory()
        evictConnections()
        val clearedBackoffKeys = try {
            BACKOFF_PATTERNS.sumOf { cache.deleteMatching(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logger.warn("youtube_egress event=backoff_clear_failed error={}", error.message)
            0
        }
        val tokenSessionRecoveryTriggered = recoverTokenSession(normalizedReason)
        logger.warn(
            "youtube_egress event=reset reason={} clearedFailureEntries={} clearedBackoffKeys={} tokenSessionRecoveryTriggered={}",
            normalizedReason,
            clearedFailureEntries,
            clearedBackoffKeys,
            tokenSessionRecoveryTriggered,
        )
        return YoutubeEgressResetResult.Completed(
            YoutubeEgressResetResponse(
                clearedFailureEntries = clearedFailureEntries,
                clearedBackoffKeys = clearedBackoffKeys,
                tokenSessionRecoveryTriggered = tokenSessionRecoveryTriggered,
            ),
        )
    }

    @Synchronized
    private fun tryAcquireReset(): Long {
        if (restMs <= 0L) return 0L
        val now = nowMs()
        val previous = lastResetAtMs
        if (previous != null) {
            val elapsed = now - previous
            if (elapsed < restMs) {
                return ((restMs - elapsed + 999L) / 1000L).coerceAtLeast(1L)
            }
        }
        lastResetAtMs = now
        return 0L
    }

    companion object {
        const val TOKEN_ENV = "YOUTUBE_EGRESS_RESET_TOKEN"
        const val DEFAULT_REASON = "egress_reset"
        const val DEFAULT_REST_MS = 120_000L
        // Public browsing backoffs (search, channels, playlists...) are keyed by hash, not by provider, so a
        // reset clears them all: a Bilibili or NicoNico entry only gets asked once more.
        private val BACKOFF_PATTERNS = listOf("youtube-related:v1:*:backoff", "*:v2:*:backoff")
        private val logger = LoggerFactory.getLogger(YoutubeEgressResetService::class.java)
    }
}
