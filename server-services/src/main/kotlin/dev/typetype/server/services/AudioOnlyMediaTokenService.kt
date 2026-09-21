package dev.typetype.server.services

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AudioOnlyMediaTokenService(
    secret: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val key = MessageDigest.getInstance("SHA-256")
        .digest("typetype.audio-only.$secret".toByteArray(Charsets.UTF_8))

    fun createToken(
        userId: String?,
        videoUrl: String,
        preferOriginal: Boolean,
        preferredLocale: String?,
        selectedItag: Int,
        selectedAudioTrackId: String?,
    ): String {
        val expiresAt = nowMillis() + TTL_SECONDS * 1000L
        val payload = listOf(
            VERSION,
            encode(userId.orEmpty()),
            encode(videoUrl),
            preferOriginal.toString(),
            encode(preferredLocale.orEmpty()),
            selectedItag.toString(),
            encode(selectedAudioTrackId.orEmpty()),
            expiresAt.toString(),
        ).joinToString(".")
        return "$payload.${signature(payload)}"
    }

    fun verify(raw: String): AudioOnlyMediaTokenResult {
        if (raw.length > MAX_TOKEN_LENGTH) return AudioOnlyMediaTokenResult.Invalid
        val parts = raw.split(".")
        if (parts.size != PART_COUNT || parts[0] != VERSION) return AudioOnlyMediaTokenResult.Invalid
        val payload = parts.take(PART_COUNT - 1).joinToString(".")
        if (!sameSignature(parts.last(), signature(payload))) return AudioOnlyMediaTokenResult.Invalid
        val expiresAt = parts[7].toLongOrNull() ?: return AudioOnlyMediaTokenResult.Invalid
        if (expiresAt <= nowMillis()) return AudioOnlyMediaTokenResult.Expired
        val userId = decode(parts[1])?.ifBlank { null }
        val videoUrl = decode(parts[2]) ?: return AudioOnlyMediaTokenResult.Invalid
        val preferOriginal = parts[3].toBooleanStrictOrNull() ?: return AudioOnlyMediaTokenResult.Invalid
        val preferredLocale = decode(parts[4])?.ifBlank { null }
        val selectedItag = parts[5].toIntOrNull() ?: return AudioOnlyMediaTokenResult.Invalid
        val selectedAudioTrackId = decode(parts[6])?.ifBlank { null }
        return AudioOnlyMediaTokenResult.Valid(
            AudioOnlyMediaToken(userId, videoUrl, preferOriginal, preferredLocale, selectedItag, selectedAudioTrackId, expiresAt)
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
        private const val VERSION = "v2"
        private const val PART_COUNT = 9
        private const val MAX_TOKEN_LENGTH = 4096
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()
    }
}
