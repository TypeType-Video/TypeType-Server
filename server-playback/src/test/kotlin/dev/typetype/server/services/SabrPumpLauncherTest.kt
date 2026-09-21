package dev.typetype.server.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SabrPumpCoordinatorTest {
    @Test
    fun `wake interrupts an idle pump wait`() = runTest {
        val coordinator = SabrPumpCoordinator()
        val observedVersion = coordinator.wakeVersion()
        var resumed = false
        launch {
            coordinator.awaitWake(observedVersion, LIVE_EDGE_POLL_MS)
            resumed = true
        }

        runCurrent()
        assertFalse(resumed)
        coordinator.wake()
        runCurrent()

        assertTrue(resumed)
        assertTrue(testScheduler.currentTime < LIVE_EDGE_POLL_MS)
    }

    @Test
    fun `stale wake does not consume a later wake`() = runTest {
        val coordinator = SabrPumpCoordinator()
        coordinator.wake()
        val observedVersion = coordinator.wakeVersion()
        var resumed = false
        launch {
            coordinator.awaitWake(observedVersion, LIVE_EDGE_POLL_MS)
            resumed = true
        }

        runCurrent()
        assertFalse(resumed)
        coordinator.wake()
        runCurrent()

        assertTrue(resumed)
        assertTrue(testScheduler.currentTime < LIVE_EDGE_POLL_MS)
    }
}
