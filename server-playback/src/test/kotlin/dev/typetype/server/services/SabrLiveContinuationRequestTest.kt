package dev.typetype.server.services

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat

class SabrInitializationPolicyTest {
    @Test
    fun `audio only warmup excludes video initialization`() {
        val audio = mockk<YoutubeSabrFormat>()
        val video = mockk<YoutubeSabrFormat>()

        assertEquals(listOf(audio), SabrInitializationPolicy.warmFormats(true, audio, video))
        assertEquals(listOf(video, audio), SabrInitializationPolicy.warmFormats(false, audio, video))
    }

    @Test
    fun `audio only fetch never waits for video initialization`() {
        assertFalse(SabrInitializationPolicy.requiresVideoFirst(true, true, 60_000L))
        assertFalse(SabrInitializationPolicy.requiresVideoFirst(false, true, 0L))
        assertFalse(SabrInitializationPolicy.requiresVideoFirst(false, false, 60_000L))
        assertTrue(SabrInitializationPolicy.requiresVideoFirst(false, true, 60_000L))
    }
}
