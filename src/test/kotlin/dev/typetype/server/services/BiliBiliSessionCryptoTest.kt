package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BiliBiliSessionCryptoTest {
    private val crypto = BiliBiliSessionCrypto.fromSecret("test-secret-key-at-least-32-chars!!")

    @Test
    fun `encrypt and decrypt round-trips`() {
        val cookie = "SESSDATA=abc123; bili_jct=csrf456; buvid3=device789"
        val encrypted = crypto.encrypt(cookie)
        assertNotEquals(cookie, encrypted)
        assertEquals(cookie, crypto.decrypt(encrypted))
    }

    @Test
    fun `encryption produces different ciphertexts`() {
        val cookie = "SESSDATA=same"
        assertNotEquals(crypto.encrypt(cookie), crypto.encrypt(cookie))
    }

    @Test
    fun `decrypt rejects tampered payload`() {
        val encrypted = crypto.encrypt("SESSDATA=test")
        val tampered = encrypted.dropLast(4) + "AAAA"
        assertThrows<Exception> { crypto.decrypt(tampered) }
    }

    @Test
    fun `rejects short secret`() {
        assertThrows<IllegalArgumentException> { BiliBiliSessionCrypto.fromSecret("short") }
    }
}
