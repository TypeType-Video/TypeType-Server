package dev.typetype.server.services

import java.util.UUID

class AuthTokenIssuer(
    private val accessCodec: AuthAccessTokenCodec,
    private val sessionStore: AuthSessionStore,
    private val sessionConfig: AuthSessionConfig,
) {
    fun issue(userId: String, sessionId: String = UUID.randomUUID().toString()): AuthSessionTokens? {
        val now = System.currentTimeMillis()
        val refreshToken = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val refreshHash = AuthRefreshTokenHasher.hash(refreshToken)
        val accessToken = accessCodec.issue(userId = userId, sessionId = sessionId)
        val expiresAt = now + sessionConfig.refreshTtlMs
        val ok = sessionStore.upsert(
            sessionId = sessionId,
            userId = userId,
            accessToken = accessToken,
            refreshHash = refreshHash,
            now = now,
            expiresAt = expiresAt,
        )
        return if (ok) AuthSessionTokens(accessToken = accessToken, refreshToken = refreshToken) else null
    }
}
