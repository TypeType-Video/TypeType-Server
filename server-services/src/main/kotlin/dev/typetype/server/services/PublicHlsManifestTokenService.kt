package dev.typetype.server.services

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PublicHlsManifestTokenService(
    secret: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val key = MessageDigest.getInstance("SHA-256")
        .digest("typetype.public-hls-manifest.$secret".toByteArray(Charsets.UTF_8))

    fun createToken(manifestUrl: String): String {
        val expiresAt = nowMillis() + TTL_SECONDS * 1000L
        val payload = listOf(VERSION, encode(manifestUrl), expiresAt.toString()).joinToString(".")
        return "$payload.${signature(payload)}"
    }

    fun createPath(manifestUrl: String): String = "/streams/hls-manifest?token=${createToken(manifestUrl)}"

    fun verify(raw: String): PublicHlsManifestTokenResult {
        val parts = raw.split(".")
        if (raw.length > MAX_TOKEN_LENGTH || parts.size != PART_COUNT || parts[0] != VERSION) {
            return PublicHlsManifestTokenResult.Invalid
        }
        val payload = parts.take(PART_COUNT - 1).joinToString(".")
        if (!sameSignature(parts.last(), signature(payload))) return PublicHlsManifestTokenResult.Invalid
        val expiresAt = parts[2].toLongOrNull() ?: return PublicHlsManifestTokenResult.Invalid
        if (expiresAt <= nowMillis()) return PublicHlsManifestTokenResult.Expired
        val manifestUrl = decode(parts[1]) ?: return PublicHlsManifestTokenResult.Invalid
        return PublicHlsManifestTokenResult.Valid(PublicHlsManifestToken(manifestUrl, expiresAt))
    }

    private fun signature(payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return encoder.encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
    }

    private fun sameSignature(left: String, right: String): Boolean {
        val leftBytes = runCatching { decoder.decode(left) }.getOrNull() ?: return false
        val rightBytes = runCatching { decoder.decode(right) }.getOrNull() ?: return false
        return MessageDigest.isEqual(leftBytes, rightBytes)
    }

    private fun encode(value: String): String = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String? = runCatching { String(decoder.decode(value), Charsets.UTF_8) }.getOrNull()

    companion object {
        const val TTL_SECONDS = 900L
        private const val VERSION = "ph1"
        private const val PART_COUNT = 4
        private const val MAX_TOKEN_LENGTH = 4096
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()
    }
}
