package dev.typetype.server.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.UUID

class AuthAccessTokenCodec(private val jwtSecret: String) {
    fun issue(userId: String, sessionId: String): String {
        val expiresAt = Date(System.currentTimeMillis() + ACCESS_TTL_MS)
        return JWT.create()
            .withJWTId(UUID.randomUUID().toString())
            .withSubject(userId)
            .withClaim(SESSION_ID_CLAIM, sessionId)
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    fun verify(token: String): AccessPrincipal? = runCatching {
        val decoded = JWT.require(Algorithm.HMAC256(jwtSecret)).build().verify(token)
        val userId = decoded.subject ?: return null
        val sessionId = decoded.getClaim(SESSION_ID_CLAIM).asString()
        AccessPrincipal(userId = userId, sessionId = sessionId)
    }.getOrNull()

    data class AccessPrincipal(val userId: String, val sessionId: String?)

    companion object {
        private const val SESSION_ID_CLAIM = "sid"
        const val ACCESS_TTL_MS = 60 * 60 * 1000L
    }
}
