package dev.typetype.server.services

import dev.typetype.server.models.YoutubeRemoteBrowserCompleteRequest
import dev.typetype.server.models.YoutubeRemoteBrowserStartResponse
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.withTimeoutOrNull

class YoutubeRemoteBrowserService(
    private val config: YoutubeRemoteBrowserConfig,
    private val adminSettingsService: AdminSettingsService,
    private val youtubeSessionService: YoutubeSessionService,
    private val client: YoutubeRemoteBrowserClient,
    private val sessions: YoutubeRemoteBrowserSessionStore = YoutubeRemoteBrowserSessionStore(),
) {
    suspend fun start(userId: String, returnTo: String?): YoutubeRemoteBrowserStartResult {
        if (!adminSettingsService.get().youtubeRemoteLoginEnabled) {
            return YoutubeRemoteBrowserStartResult.Disabled
        }
        if (!youtubeSessionService.isConfigured) {
            return YoutubeRemoteBrowserStartResult.Misconfigured
        }
        val internalToken = config.internalToken ?: return YoutubeRemoteBrowserStartResult.Misconfigured
        val result = when (val reserved = sessions.reserve(userId, config)) {
            YoutubeRemoteBrowserReserveResult.AlreadyActive -> YoutubeRemoteBrowserStartResult.AlreadyActive
            YoutubeRemoteBrowserReserveResult.CapacityReached -> YoutubeRemoteBrowserStartResult.CapacityReached
            is YoutubeRemoteBrowserReserveResult.Reserved -> startTokenSession(reserved, userId, internalToken, returnTo)
        }
        YoutubeRemoteBrowserAudit.startResult(userId, result::class.simpleName ?: "unknown")
        return result
    }

    suspend fun cancel(userId: String, sessionId: String): Boolean {
        val tokenSessionId = sessions.cancel(userId, sessionId) ?: return false
        val internalToken = config.internalToken ?: return true
        client.cancel(tokenSessionId, internalToken)
        return true
    }

    suspend fun complete(
        request: YoutubeRemoteBrowserCompleteRequest,
        internalToken: String?,
    ): YoutubeRemoteBrowserCompleteResult {
        if (!isInternalAuthorized(internalToken)) return YoutubeRemoteBrowserCompleteResult.Unauthorized
        if (request.status != "completed") return YoutubeRemoteBrowserCompleteResult.InvalidPayload
        if (!youtubeSessionService.isConfigured) return YoutubeRemoteBrowserCompleteResult.Unavailable
        val session = sessions.complete(request.sessionId, request.tokenSessionId)
            ?: return YoutubeRemoteBrowserCompleteResult.NotFound.also { YoutubeRemoteBrowserAudit.completion(request, it) }
        val result = youtubeSessionService
            .completeRemote(session.userId, request.cookies, request.poToken, request.authUser)
            .toRemoteBrowserResult()
        YoutubeRemoteBrowserAudit.completion(request, result)
        return result
    }

    suspend fun bridge(sessionId: String, wsToken: String?, serverSession: DefaultWebSocketServerSession): Unit {
        val session = wsToken?.let { sessions.authenticateWebSocket(sessionId, it) }
        val tokenSessionId = session?.tokenSessionId
        val internalToken = config.internalToken
        if (session == null || tokenSessionId == null || internalToken == null) {
            serverSession.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            return
        }
        try {
            client.bridge(serverSession, tokenSessionId, internalToken, config)
        } finally {
            cancel(session.userId, session.sessionId)
        }
    }

    private suspend fun startTokenSession(
        reserved: YoutubeRemoteBrowserReserveResult.Reserved,
        userId: String,
        internalToken: String,
        returnTo: String?,
    ): YoutubeRemoteBrowserStartResult {
        if ((returnTo?.length ?: 0) > MAX_RETURN_TO_LENGTH) {
            sessions.remove(reserved.session.sessionId)
            return YoutubeRemoteBrowserStartResult.TokenUnavailable
        }
        val tokenResponse = withTimeoutOrNull(START_TIMEOUT_MS) {
            client.start(
                YoutubeRemoteBrowserTokenStartRequest(
                    serverSessionId = reserved.session.sessionId,
                    userId = userId,
                    callbackUrl = config.callbackUrl,
                    ttlMs = config.ttlMs,
                ),
                internalToken,
            )
        }
        if (tokenResponse == null) {
            sessions.remove(reserved.session.sessionId)
            return YoutubeRemoteBrowserStartResult.TokenUnavailable
        }
        val attached = sessions.attachTokenSession(reserved.session.sessionId, tokenResponse.sessionId, tokenResponse.expiresAt)
            ?: return YoutubeRemoteBrowserStartResult.TokenUnavailable
        return YoutubeRemoteBrowserStartResult.Started(
            YoutubeRemoteBrowserStartResponse(
                sessionId = attached.sessionId,
                wsUrl = "/youtube-session/browser/${attached.sessionId}?token=${reserved.wsToken}",
                expiresAt = attached.expiresAt,
            )
        )
    }

    private fun isInternalAuthorized(value: String?): Boolean =
        value != null && value == config.internalToken

    companion object {
        private const val START_TIMEOUT_MS = 10_000L
        private const val MAX_RETURN_TO_LENGTH = 2048
    }
}
