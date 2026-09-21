package dev.typetype.server

import dev.typetype.server.services.RssFeedSecret
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RssFeedSecretTest {
    private val secrets = RssFeedSecret()

    @Test
    fun `secret is random and only its hash is comparable`() {
        val first = secrets.create()
        val second = secrets.create()
        val hash = secrets.hash(first)

        assertNotEquals(first, second)
        assertNotEquals(first, hash)
        assertTrue(secrets.matches(first, hash))
        assertFalse(secrets.matches(second, hash))
    }
}
