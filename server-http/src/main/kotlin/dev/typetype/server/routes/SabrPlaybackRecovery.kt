package dev.typetype.server.routes

import dev.typetype.server.services.SABR_TOKEN_BINDING_FAILURE
import dev.typetype.server.services.SABR_RECOVERABLE_FAILURE_PREFIX
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import java.util.concurrent.ConcurrentHashMap

/**
 * A session YouTube asks to attest keeps the token session it was opened with, so only a fresh one can
 * play on. While the server replaces a token session put on hold, a fresh session would still get the
 * old one: the failure is held back, up to [holdMs], until the replacement is done.
 */
internal class SabrPlaybackRecovery(
    private val sessionStore: SabrSessionStore,
    private val holdMs: Long = TOKEN_SESSION_HOLD_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val held = ConcurrentHashMap<String, Long>()

    suspend fun action(holder: SabrSessionHolder): String? {
        val terminalFailure = holder.terminalFailure()
        val failure = terminalFailure ?: holder.networkFailure() ?: return null
        if (terminalFailure == null || failure.startsWith(SABR_RECOVERABLE_FAILURE_PREFIX)) {
            sessionStore.invalidatePlaybackInfo(holder.key.videoId)
            return RETRY_FRESH_SESSION
        }
        if (failure.contains("Expected UMP response", ignoreCase = true)) {
            sessionStore.invalidatePlaybackInfo(holder.key.videoId)
            return RETRY_FRESH_SESSION
        }
        if (isProtection(failure)) {
            sessionStore.recoverProtectedPlaybackInfo(holder)
            return if (awaitsTokenSession(holder)) null else RETRY_FRESH_SESSION
        }
        if (failure.contains("SABR demand stalled")) {
            sessionStore.invalidatePlaybackInfo(holder.key.videoId)
            return RETRY_FRESH_SESSION
        }
        if (failure.contains("upstream unauthorized HTTP 403") || failure.contains(SABR_TOKEN_BINDING_FAILURE)) {
            sessionStore.invalidatePlaybackInfo(holder.key.videoId)
            return RETRY_FRESH_SESSION
        }
        return null
    }

    fun retryVideoItags(): List<Int> = emptyList()

    fun awaitsTokenSession(holder: SabrSessionHolder): Boolean {
        val failure = holder.terminalFailure()
        if (failure == null || !isProtection(failure) || !sessionStore.tokenSessionRenewing) {
            held.remove(holder.sessionToken)
            return false
        }
        val time = now()
        held.entries.removeIf { time - it.value > STALE_HOLD_MS }
        return time - held.getOrPut(holder.sessionToken) { time } < holdMs
    }

    private fun isProtection(failure: String): Boolean =
        failure.contains("protected no-media") ||
            failure.contains("attestation required", ignoreCase = true) ||
            failure.contains("attestation remained pending", ignoreCase = true)

    companion object {
        const val TOKEN_SESSION_BLOCKER = "token_session_renewal"
        const val TOKEN_SESSION_RETRY_MS = 1_000L
        private const val RETRY_FRESH_SESSION = "retry_fresh_session"
        private const val TOKEN_SESSION_HOLD_MS = 45_000L
        private const val STALE_HOLD_MS = 300_000L
    }
}
