package dev.typetype.server.services

import java.security.MessageDigest

object PublicCacheKey {
    private val hex = "0123456789abcdef".toCharArray()

    fun of(area: String, vararg parts: String?): String = "$area:v2:${digest(parts)}"

    private fun digest(parts: Array<out String?>): String {
        val raw = parts.joinToString(separator = "\u001f") { it.orEmpty() }
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).toHex().take(32)
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        this@toHex.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}
