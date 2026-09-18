package dev.typetype.server.services

import java.security.MessageDigest

object AuthRefreshTokenHasher {
    fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
