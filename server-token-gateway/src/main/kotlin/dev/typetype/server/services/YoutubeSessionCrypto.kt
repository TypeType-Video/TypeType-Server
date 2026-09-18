package dev.typetype.server.services

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class YoutubeSessionCrypto private constructor(private val key: SecretKeySpec) {
    private val random = SecureRandom()

    fun encrypt(value: String): String {
        val nonce = ByteArray(NONCE_BYTES)
        random.nextBytes(nonce)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + encoder.encodeToString(nonce + encrypted)
    }

    fun decrypt(value: String): String {
        require(value.startsWith(PREFIX)) { "Invalid encrypted payload format" }
        val bytes = decoder.decode(value.removePrefix(PREFIX))
        require(bytes.size > NONCE_BYTES) { "Invalid encrypted payload" }
        val nonce = bytes.copyOfRange(0, NONCE_BYTES)
        val encrypted = bytes.copyOfRange(NONCE_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(AAD)
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    companion object {
        private const val PREFIX = "gcm256."
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val MIN_SECRET_LENGTH = 32
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val AAD = "typetype.youtube-session".toByteArray(Charsets.UTF_8)
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()

        fun fromSecret(secret: String): YoutubeSessionCrypto {
            require(secret.length >= MIN_SECRET_LENGTH) { "YouTube session encryption key is too short" }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(secret.toByteArray(Charsets.UTF_8))
            return YoutubeSessionCrypto(SecretKeySpec(digest, "AES"))
        }
    }
}
