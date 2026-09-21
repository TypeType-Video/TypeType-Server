package dev.typetype.server.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.UUID

class OidcStateCodec(jwtSecret: String) {
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    fun create(nonce: String, redirectUri: String, returnTo: String): String = JWT.create()
        .withJWTId(UUID.randomUUID().toString())
        .withClaim("nonce", nonce)
        .withClaim("redirectUri", redirectUri)
        .withClaim("returnTo", returnTo)
        .withExpiresAt(Date(System.currentTimeMillis() + TTL_MS))
        .sign(algorithm)

    fun verify(raw: String): OidcState {
        val jwt = JWT.require(algorithm).build().verify(raw)
        return OidcState(
            nonce = jwt.getClaim("nonce").asString(),
            redirectUri = jwt.getClaim("redirectUri").asString(),
            returnTo = jwt.getClaim("returnTo").asString(),
        )
    }

    companion object {
        private const val TTL_MS = 10 * 60 * 1000L
    }
}
