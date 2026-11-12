package dev.typetype.server.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TokenSessionWatchTest {
    private class Check(var accepted: Boolean) : EgressExitCheck {
        var renewed = 0
        var probes = 0
        override suspend fun renew(videoId: String) { renewed++; accepted = true }
        override suspend fun accepts(videoId: String): Boolean { probes++; return accepted }
    }

    @Test
    fun `a new session on hold is replaced before a viewer meets it`() = runTest {
        val check = Check(accepted = false)
        val acceptance = TokenSessionAcceptance(check, rotation = null, scope = this, now = { testScheduler.currentTime })
        val watch = TokenSessionWatch({ "gen-1" }, check, acceptance)
        watch.played("video")

        watch.once()
        advanceUntilIdle()

        assertEquals(1, check.renewed)
    }

    @Test
    fun `a session is checked once and nothing is checked before a video was played`() = runTest {
        val check = Check(accepted = true)
        val acceptance = TokenSessionAcceptance(check, rotation = null, scope = this, now = { testScheduler.currentTime })
        var generation = "gen-1"
        val watch = TokenSessionWatch({ generation }, check, acceptance)

        watch.once()
        assertEquals(0, check.probes)
        watch.played("video")
        watch.once()
        watch.once()
        assertEquals(1, check.probes)
        generation = "gen-2"
        watch.once()

        assertEquals(2, check.probes)
        assertEquals(0, check.renewed)
    }
}
