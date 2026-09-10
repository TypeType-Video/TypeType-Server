package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class HomeWarmupTrackerTest {
    @Test
    fun `ordinary concurrent requests start only one warmup`() {
        val tracker = HomeWarmupTracker(100L, 1_000L)
        tracker.markActive("user", 0L)
        Executors.newFixedThreadPool(8).use { executor ->
            val results = executor.invokeAll(List(100) {
                Callable { tracker.trySchedule("user", 0L, false) }
            })
            assertEquals(1, results.count { it.get() })
        }
        assertFalse(tracker.trySchedule("user", 99L, false))
        assertTrue(tracker.trySchedule("user", 100L, false))
    }

    @Test
    fun `forced invalidation retains its immediate warmup behavior`() {
        val tracker = HomeWarmupTracker(100L, 1_000L)
        assertTrue(tracker.trySchedule("user", 0L, false))
        assertTrue(tracker.trySchedule("user", 1L, true))
        assertFalse(tracker.trySchedule("user", 2L, false))
    }

    @Test
    fun `expiry removes both activity and throttle entries`() {
        val tracker = HomeWarmupTracker(10_000L, 1_000L)
        repeat(10_000) { index ->
            tracker.markActive("user-$index", 0L)
            tracker.trySchedule("user-$index", 0L, false)
        }
        tracker.markActive("retained", 1_000L)
        assertEquals(listOf("retained"), tracker.activeUsers(1_001L))
        repeat(10_000) { index ->
            assertTrue(tracker.trySchedule("user-$index", 1_001L, false))
        }
        assertEquals(listOf("retained"), tracker.activeUsers(2_000L))
        assertTrue(tracker.activeUsers(2_001L).isEmpty())
    }
}
