package dev.typetype.server.services

import java.security.MessageDigest

object UploadedImages {
    private const val VERSION_LENGTH = 20
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val GIF_87_SIGNATURE = "GIF87a".encodeToByteArray()
    private val GIF_89_SIGNATURE = "GIF89a".encodeToByteArray()
    private val WEBP_RIFF = "RIFF".encodeToByteArray()
    private val WEBP_SIGNATURE = "WEBP".encodeToByteArray()

    fun mediaType(bytes: ByteArray): String? = when {
        bytes.startsWith(PNG_SIGNATURE) -> "image/png"
        bytes.startsWith(JPEG_SIGNATURE) -> "image/jpeg"
        bytes.startsWith(GIF_87_SIGNATURE) || bytes.startsWith(GIF_89_SIGNATURE) -> "image/gif"
        bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals(WEBP_RIFF) &&
            bytes.copyOfRange(8, 12).contentEquals(WEBP_SIGNATURE) -> "image/webp"
        else -> null
    }

    fun version(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
        .take(VERSION_LENGTH)

    private fun ByteArray.startsWith(signature: ByteArray): Boolean = size >= signature.size &&
        copyOfRange(0, signature.size).contentEquals(signature)
}
