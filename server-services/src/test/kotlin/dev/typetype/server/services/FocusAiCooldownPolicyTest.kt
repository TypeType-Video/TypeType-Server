package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusAiCooldownPolicyTest {
    @Test
    fun `approves strictly above pass score and rejects at or below pass score`() {
        assertFalse(FocusAiCooldownPolicy.isApproved(0))
        assertFalse(FocusAiCooldownPolicy.isApproved(80))
        assertTrue(FocusAiCooldownPolicy.isApproved(81))
        assertTrue(FocusAiCooldownPolicy.isApproved(100))
    }

    @Test
    fun `maps rejected scores to bounded linear cooling-off durations`() {
        assertEquals(60 * 60_000L, FocusAiCooldownPolicy.cooldownMs(0))
        assertEquals(60 * 60_000L, FocusAiCooldownPolicy.cooldownMs(10))
        assertEquals(30 * 60_000L, FocusAiCooldownPolicy.cooldownMs(45))
        assertEquals(1 * 60_000L, FocusAiCooldownPolicy.cooldownMs(80))
        assertEquals(0L, FocusAiCooldownPolicy.cooldownMs(81))
        assertEquals(0L, FocusAiCooldownPolicy.cooldownMs(100))
    }
}
