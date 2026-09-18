package dev.typetype.server.services

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SignedHlsManifestTokenService(
    secret: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val key = MessageDigest.getInstance("SHA-256")
        .digest("typetype.hls-manifest.$secret".toByteArray(Charsets.UTF_8))

    fun createToken(userId: String, videoUrl: String, fingerprint: String): String {
        val expiresAt = nowMillis() + TTL_SECONDS * 1000L
        val payload = listOf(
            VERSION,
            encode(userId),
            encode(videoUrl),
            encode(fingerprint),
            expiresAt.toString(),
        ).joinToString(".")
        return "$payload.${signature(payload)}"
    }

    fun createPath(userId: String, videoUrl: String, fingerprint: String): String =
        "/streams/hls-manifest?token=${createToken(userId, videoUrl, fingerprint)}"

    fun verify(raw: String): SignedHlsManifestTokenResult {
        if (raw.length > MAX_TOKEN_LENGTH) return SignedHlsManifestTokenResult.Invalid
        val parts = raw.split(".")
        if (parts.size != PART_COUNT || parts[0] != VERSION) return SignedHlsManifestTokenResult.Invalid
        val payload = parts.take(PART_COUNT - 1).joinToString(".")
        if (!sameSignature(parts.last(), signature(payload))) return SignedHlsManifestTokenResult.Invalid
        val expiresAt = parts[4].toLongOrNull() ?: return SignedHlsManifestTokenResult.Invalid
        if (expiresAt <= nowMillis()) return SignedHlsManifestTokenResult.Expired
        val userId = decode(parts[1]) ?: return SignedHlsManifestTokenResult.Invalid
        val videoUrl = decode(parts[2]) ?: return SignedHlsManifestTokenResult.Invalid
        val fingerprint = decode(parts[3]) ?: return SignedHlsManifestTokenResult.Invalid
        return SignedHlsManifestTokenResult.Valid(
            SignedHlsManifestToken(
                userId = userId,
                videoUrl = videoUrl,
                fingerprint = fingerprint,
                expiresAt = expiresAt,
            )
        )
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

    private fun encode(value: String): String =
        encoder.encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String? =
        runCatching { String(decoder.decode(value), Charsets.UTF_8) }.getOrNull()

    companion object {
        const val TTL_SECONDS = 900L
        private const val VERSION = "v1"
        private const val PART_COUNT = 6
        private const val MAX_TOKEN_LENGTH = 4096
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()
    }
}
