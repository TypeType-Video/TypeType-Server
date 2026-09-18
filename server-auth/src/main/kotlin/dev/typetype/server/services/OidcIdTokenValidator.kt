package dev.typetype.server.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

class OidcIdTokenValidator(
    private val httpClient: OidcHttpClient,
    private val keyResolver: OidcRsaKeyResolver = OidcRsaKeyResolver(),
) {
    fun validate(config: OidcConfig, metadata: OidcProviderMetadata, idToken: String, nonce: String): OidcIdentity {
        val decoded = JWT.decode(idToken)
        if (decoded.algorithm != "RS256") throw IllegalStateException("OIDC ID token algorithm is unsupported")
        val publicKey = keyResolver.resolve(httpClient.jwks(metadata.jwksUri), decoded.keyId)
        val jwt = JWT.require(Algorithm.RSA256(publicKey, null))
            .withIssuer(metadata.issuer)
            .withAudience(config.clientId)
            .withClaim("nonce", nonce)
            .build()
            .verify(idToken)
        val email = jwt.getClaim("email").asString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("OIDC email claim is missing")
        val name = jwt.getClaim("name").asString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: jwt.getClaim("preferred_username").asString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: email.substringBefore('@')
        return OidcIdentity(
            issuer = metadata.issuer,
            subject = jwt.subject,
            email = email,
            name = name,
        )
    }
}
