package dev.typetype.server.routes

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SabrPlaybackRateTest {
    @Test
    fun `accepts only rates supported by the web player`() {
        assertTrue(0.25f.isSupportedSabrPlaybackRate())
        assertTrue(1.0f.isSupportedSabrPlaybackRate())
        assertTrue(4.0f.isSupportedSabrPlaybackRate())
        assertFalse(0.0f.isSupportedSabrPlaybackRate())
        assertFalse(4.01f.isSupportedSabrPlaybackRate())
        assertFalse(Float.NaN.isSupportedSabrPlaybackRate())
    }
}
