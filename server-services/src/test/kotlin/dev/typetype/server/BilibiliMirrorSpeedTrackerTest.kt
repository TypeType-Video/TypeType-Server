package dev.typetype.server

import dev.typetype.server.services.BilibiliMirrorSpeedTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BilibiliMirrorSpeedTrackerTest {
    @BeforeEach
    fun setup() {
        BilibiliMirrorSpeedTracker.reset()
    }

    @Test
    fun `records success latency and prioritizes lower latency mirror`() {
        val slowMirror = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s"
        val fastMirror = "https://upos-sz-mirroraliov.bilivideo.com/video.m4s"

        BilibiliMirrorSpeedTracker.recordSuccess(slowMirror, 450)
        BilibiliMirrorSpeedTracker.recordSuccess(fastMirror, 30)

        val prioritized = BilibiliMirrorSpeedTracker.prioritize(listOf(slowMirror, fastMirror))
        assertEquals(listOf(fastMirror, slowMirror), prioritized)
    }

    @Test
    fun `penalizes failing mirror so other mirrors take precedence`() {
        val failingMirror = "https://upos-sz-mirroraliov.bilivideo.com/video.m4s"
        val fallbackMirror = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s"

        // Both recorded with initial latency
        BilibiliMirrorSpeedTracker.recordSuccess(failingMirror, 20)
        BilibiliMirrorSpeedTracker.recordSuccess(fallbackMirror, 200)

        // Then failingMirror experiences failures
        BilibiliMirrorSpeedTracker.recordFailure(failingMirror)

        val prioritized = BilibiliMirrorSpeedTracker.prioritize(listOf(failingMirror, fallbackMirror))
        assertEquals(listOf(fallbackMirror, failingMirror), prioritized)
        assertTrue(BilibiliMirrorSpeedTracker.score(failingMirror) > BilibiliMirrorSpeedTracker.score(fallbackMirror))
    }
}
