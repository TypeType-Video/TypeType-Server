package dev.typetype.server.services

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class RssFeedSecret(private val random: SecureRandom = SecureRandom()) {
    fun create(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(secret: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)),
    )

    fun matches(secret: String, expectedHash: String): Boolean {
        val actual = runCatching { Base64.getUrlDecoder().decode(hash(secret)) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getUrlDecoder().decode(expectedHash) }.getOrNull() ?: return false
        return MessageDigest.isEqual(actual, expected)
    }
}
