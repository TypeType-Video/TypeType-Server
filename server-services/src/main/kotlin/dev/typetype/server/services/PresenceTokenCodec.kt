package dev.typetype.server.services

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PresenceTokenCodec {
    const val PREFIX = "ttp1_"
    private val random = SecureRandom()

    fun issue(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun prefix(token: String): String = token.take(PREFIX.length + 6)

    fun hash(token: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)),
    )

    fun sameHash(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))
}
