package dev.typetype.server.services

class AuthSessionRevoker(private val sessionStore: AuthSessionStore) {
    fun revokeByRefreshToken(refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) return
        val hash = AuthRefreshTokenHasher.hash(refreshToken)
        sessionStore.revokeByRefreshHash(hash, System.currentTimeMillis())
    }
}
