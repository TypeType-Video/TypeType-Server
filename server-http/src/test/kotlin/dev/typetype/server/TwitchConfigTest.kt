package dev.typetype.server

import dev.typetype.server.services.TwitchConfig
import dev.typetype.server.services.TwitchTokenCrypto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitchConfigTest {
    private fun config(values: Map<String, String>) = TwitchConfig.fromEnvironment(env = { values[it] }, secret = { values[it] })

    @Test
    fun `twitch stays off until url secret and a long enough key are set`() {
        assertFalse(config(emptyMap()).enabled)
        assertFalse(config(mapOf("TWITCH_SERVICE_URL" to "http://live:8080", "TWITCH_SERVICE_SECRET" to "s")).enabled)
        val short = mapOf("TWITCH_SERVICE_URL" to "http://live:8080", "TWITCH_SERVICE_SECRET" to "s", "TWITCH_TOKEN_ENCRYPTION_KEY" to "k")
        assertFalse(config(short).enabled)
        val full = short + ("TWITCH_TOKEN_ENCRYPTION_KEY" to "k".repeat(32))
        assertTrue(config(full).enabled)
        assertEquals("http://live:8080", config(full + ("TWITCH_SERVICE_URL" to "http://live:8080/")).serviceUrl)
    }

    @Test
    fun `token crypto round trips and rejects another key`() {
        val crypto = TwitchTokenCrypto("a".repeat(32))
        val sealed = crypto.encrypt("token")
        assertNotEquals(sealed, crypto.encrypt("token"))
        assertEquals("token", crypto.decrypt(sealed))
        assertThrows(Exception::class.java) { TwitchTokenCrypto("b".repeat(32)).decrypt(sealed) }
    }
}
