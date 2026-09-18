package dev.typetype.server.services

class AuthSessionVerifier(
    private val accessCodec: AuthAccessTokenCodec,
    private val sessionStore: AuthSessionStore,
) {
    fun verifyUserId(token: String): String? {
        val principal = accessCodec.verify(token) ?: return null
        if (principal.userId.startsWith("guest:")) return principal.userId
        val sessionId = principal.sessionId ?: return null
        val row = sessionStore.findBySessionId(sessionId) ?: return null
        val now = System.currentTimeMillis()
        if (row.userId != principal.userId) return null
        if (row.revokedAt != null) return null
        if (row.expiresAt <= now) return null
        return principal.userId
    }
}
