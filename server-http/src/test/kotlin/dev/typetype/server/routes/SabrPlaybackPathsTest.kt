package dev.typetype.server.routes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SabrPlaybackPathsTest {
    @Test
    fun `redactedSabrPath replaces full session token in path segments`() {
        val token = "secret-session-token-123456"
        val windowPath = redactedSabrPath("/sabr/playback/$token/window", token)
        assertEquals("/sabr/playback/{sessionId}/window", windowPath)
        assertFalse(windowPath.contains(token))

        val segmentPath = redactedSabrPath("/api/sabr/playback/$token/137/segment/4", token)
        assertEquals("/api/sabr/playback/{sessionId}/137/segment/4", segmentPath)
        assertFalse(segmentPath.contains(token))
    }

    @Test
    fun `redactedSabrPath falls back to metricPath when session segment is absent`() {
        assertEquals("/sabr/playback/{sessionId}", redactedSabrPath("/sabr/playback/other-token/manifest", "expected-token"))
        assertEquals("/sabr/playback/{sessionId}", redactedSabrPath("/sabr/playback/some-token/seek", ""))
    }
}
