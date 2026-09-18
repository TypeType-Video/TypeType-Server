package dev.typetype.server.services

class AuthSessionRefresher(
    private val sessionStore: AuthSessionStore,
    private val tokenIssuer: AuthTokenIssuer,
) {
    fun refresh(refreshToken: String): AuthSessionTokens? {
        val refreshHash = AuthRefreshTokenHasher.hash(refreshToken)
        val row = sessionStore.findByRefreshHash(refreshHash) ?: return null
        val now = System.currentTimeMillis()
        if (row.revokedAt != null) return null
        if (row.expiresAt <= now) return null
        return tokenIssuer.issue(userId = row.userId, sessionId = row.sessionId)
    }
}
