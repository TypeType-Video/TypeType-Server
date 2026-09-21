package dev.typetype.server

import dev.typetype.server.services.YoutubeSessionCrypto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeSessionCryptoTest {
    @Test
    fun `encrypts and decrypts without storing plaintext`() {
        val crypto = YoutubeSessionCrypto.fromSecret("test-youtube-session-key-32-bytes")
        val encrypted = crypto.encrypt("SID=secret-cookie")
        assertTrue(encrypted.startsWith("gcm256."))
        assertFalse(encrypted.contains("secret-cookie"))
        assertEquals("SID=secret-cookie", crypto.decrypt(encrypted))
    }

    @Test
    fun `rejects weak encryption key`() {
        assertThrows(IllegalArgumentException::class.java) {
            YoutubeSessionCrypto.fromSecret("short")
        }
    }
}
