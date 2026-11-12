package dev.typetype.server.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TokenSessionAcceptanceTest {
    private class Check(private val acceptedFrom: Int) : EgressExitCheck {
        var sessions = 0
        override suspend fun renew(videoId: String) { sessions++ }
        override suspend fun accepts(videoId: String): Boolean = sessions >= acceptedFrom
    }

    @Test
    fun `a session on hold is replaced until one is accepted`() = runTest {
        val check = Check(acceptedFrom = 3)
        val acceptance = TokenSessionAcceptance(check, rotation = null, scope = this, now = { testScheduler.currentTime })

        assertTrue(acceptance.onHold("sabr_attestation_pending", "video"))
        advanceUntilIdle()

        assertEquals(3, check.sessions)
    }

    @Test
    fun `it stops after its tries when no session is accepted`() = runTest {
        val check = Check(acceptedFrom = Int.MAX_VALUE)
        val acceptance = TokenSessionAcceptance(check, rotation = null, scope = this, tries = 4, now = { testScheduler.currentTime })

        acceptance.onHold("sabr_attestation", "video")
        advanceUntilIdle()

        assertEquals(4, check.sessions)
    }

    @Test
    fun `one recovery runs at a time and then rests`() = runTest {
        val check = Check(acceptedFrom = 2)
        val acceptance = TokenSessionAcceptance(check, rotation = null, scope = this, now = { testScheduler.currentTime })

        assertTrue(acceptance.onHold("sabr_attestation_pending", "video"))
        assertFalse(acceptance.onHold("sabr_attestation_pending", "other"))
        advanceUntilIdle()
        assertFalse(acceptance.onHold("sabr_attestation_pending", "video"))
        testScheduler.advanceTimeBy(120_001L)

        assertTrue(acceptance.onHold("sabr_attestation_pending", "video"))
        advanceUntilIdle()
        assertEquals(3, check.sessions)
    }
}
