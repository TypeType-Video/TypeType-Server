package dev.typetype.server.routes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SabrPlaybackWindowRetryPolicyTest {
    @Test
    fun firstPendingVodWindowUsesShortRetry() {
        assertEquals(
            100L,
            playbackWindowRetryAfterMs(
                isLive = false,
                initialWindowReady = false,
                defaultRetryAfterMs = 500L,
            ),
        )
    }

    @Test
    fun laterVodWindowsKeepDefaultRetry() {
        assertEquals(
            500L,
            playbackWindowRetryAfterMs(
                isLive = false,
                initialWindowReady = true,
                defaultRetryAfterMs = 500L,
            ),
        )
    }

    @Test
    fun liveWindowKeepsDefaultRetryBeforeInitialReadiness() {
        assertEquals(
            500L,
            playbackWindowRetryAfterMs(
                isLive = true,
                initialWindowReady = false,
                defaultRetryAfterMs = 500L,
            ),
        )
    }
}
