package dev.typetype.server.services

import java.security.SecureRandom
import java.util.Base64

object SabrSessionTokenGenerator {
    private val random = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
