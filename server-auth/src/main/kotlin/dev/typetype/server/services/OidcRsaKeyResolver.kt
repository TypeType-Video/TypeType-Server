package dev.typetype.server.services

import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

class OidcRsaKeyResolver {
    fun resolve(jwks: JSONObject, kid: String?): RSAPublicKey {
        val keys = jwks.getJSONArray("keys")
        for (index in 0 until keys.length()) {
            val key = keys.getJSONObject(index)
            if (key.optString("kty") == "RSA" && (kid == null || key.optString("kid") == kid)) {
                return key.toRsaPublicKey()
            }
        }
        throw IllegalStateException("OIDC signing key not found")
    }

    private fun JSONObject.toRsaPublicKey(): RSAPublicKey {
        val decoder = Base64.getUrlDecoder()
        val modulus = BigInteger(1, decoder.decode(getString("n")))
        val exponent = BigInteger(1, decoder.decode(getString("e")))
        val spec = RSAPublicKeySpec(modulus, exponent)
        return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }
}
