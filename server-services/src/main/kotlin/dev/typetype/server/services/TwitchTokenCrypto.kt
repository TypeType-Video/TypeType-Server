package dev.typetype.server.services

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TwitchTokenCrypto(secret: String) {
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)), "AES")
    private val random = SecureRandom()

    fun encrypt(value: String): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(AAD)
        return PREFIX + encoder.encodeToString(nonce + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(value: String): String {
        require(value.startsWith(PREFIX)) { "Invalid encrypted payload format" }
        val bytes = decoder.decode(value.removePrefix(PREFIX))
        require(bytes.size > NONCE_BYTES) { "Invalid encrypted payload" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, NONCE_BYTES)))
        cipher.updateAAD(AAD)
        return cipher.doFinal(bytes.copyOfRange(NONCE_BYTES, bytes.size)).toString(Charsets.UTF_8)
    }

    private companion object {
        const val PREFIX = "gcm256."
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val AAD = "typetype.twitch-token".toByteArray(Charsets.UTF_8)
        val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        val decoder: Base64.Decoder = Base64.getUrlDecoder()
    }
}
