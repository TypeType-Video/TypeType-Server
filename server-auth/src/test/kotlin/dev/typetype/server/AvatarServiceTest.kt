package dev.typetype.server

import dev.typetype.server.services.AvatarService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AvatarServiceTest {
    private val service = AvatarService()

    @Test
    fun `normalize accepts plain codepoint`() {
        assertEquals("1F47E", service.normalizeEmojiCode("1f47e"))
    }

    @Test
    fun `normalize strips FE0F suffix when provided`() {
        assertEquals("1F47E", service.normalizeEmojiCode("1F47E-FE0F"))
    }

    @Test
    fun `normalize rejects malformed code`() {
        assertNull(service.normalizeEmojiCode("../1F47E"))
    }
}
