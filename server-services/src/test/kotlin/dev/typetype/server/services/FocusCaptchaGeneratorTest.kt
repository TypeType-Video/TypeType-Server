package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusCaptchaGeneratorTest {
    @Test
    fun `generates unambiguous 24-char mixed-case confirmation strings`() {
        val allowed = FocusCaptchaGenerator.ALPHABET.toSet()
        val first = FocusCaptchaGenerator.generateCode()
        val second = FocusCaptchaGenerator.generateCode()

        assertEquals(24, first.length)
        assertTrue(first.all { it in allowed })
        assertFalse(first.any { it in setOf('0', 'O', 'o', '1', 'I', 'l') })
        assertNotEquals(first, second)
    }
}
