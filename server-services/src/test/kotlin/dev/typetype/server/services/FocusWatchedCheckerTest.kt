package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusWatchedCheckerTest {
    @Test
    fun `matches default last-minute and short-video 90 percent thresholds`() {
        assertFalse(FocusWatchedChecker.isWatched(0L, 600L, 0))
        assertFalse(FocusWatchedChecker.isWatched(539L, 600L, 0))
        assertTrue(FocusWatchedChecker.isWatched(540L, 600L, 0))
        assertTrue(FocusWatchedChecker.isWatched(700L, 600L, 0))

        assertFalse(FocusWatchedChecker.isWatched(44L, 50L, 0))
        assertTrue(FocusWatchedChecker.isWatched(45L, 50L, 0))
    }

    @Test
    fun `supports custom percentage thresholds between 50 and 100`() {
        assertFalse(FocusWatchedChecker.isWatched(79L, 100L, 80))
        assertTrue(FocusWatchedChecker.isWatched(80L, 100L, 80))
        assertFalse(FocusWatchedChecker.isWatched(99L, 100L, 100))
        assertTrue(FocusWatchedChecker.isWatched(100L, 100L, 100))
        assertFalse(FocusWatchedChecker.isWatched(10L, 0L, 80))
    }
}
