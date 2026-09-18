package dev.typetype.server.routes

import dev.typetype.server.services.SABR_TOKEN_BINDING_FAILURE
import dev.typetype.server.services.SABR_RECOVERABLE_FAILURE_PREFIX
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore

internal class SabrPlaybackRecovery(private val sessionStore: SabrSessionStore) {
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
        if (
            failure.contains("protected no-media") ||
            failure.contains("attestation required", ignoreCase = true)
        ) {
            sessionStore.recoverProtectedPlaybackInfo(holder)
            return RETRY_FRESH_SESSION
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

    private companion object {
        const val RETRY_FRESH_SESSION = "retry_fresh_session"
    }
}
