package dev.typetype.server.routes

import dev.typetype.server.services.SABR_TOKEN_BINDING_FAILURE
import dev.typetype.server.services.SABR_RECOVERABLE_FAILURE_PREFIX
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SabrPlaybackRecoveryTest {
    @Test
    fun `network failure invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns null
        every { holder.networkFailure() } returns "timeout"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit

        assertEquals("retry_fresh_session", SabrPlaybackRecovery(store).action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `recoverable media failure invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "$SABR_RECOVERABLE_FAILURE_PREFIX Unexpected EOF"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit

        assertEquals("retry_fresh_session", SabrPlaybackRecovery(store).action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `local spool failure does not request a fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "Could not write SABR spool file"

        assertNull(SabrPlaybackRecovery(store).action(holder))
        coVerify(exactly = 0) { store.invalidatePlaybackInfo(any()) }
    }

    @Test
    fun `stalled demand invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "SABR demand stalled for 140:39"
        every { holder.key } returns SabrSessionKey("video", "user", 140, "en-US.4", 137, 379_441L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit
        val recovery = SabrPlaybackRecovery(store)

        assertEquals("retry_fresh_session", recovery.action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `unauthorized token refresh invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "SABR upstream unauthorized HTTP 403 after TypeType token refresh"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit
        val recovery = SabrPlaybackRecovery(store)

        assertEquals("retry_fresh_session", recovery.action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `token binding mismatch invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "video:137:12 $SABR_TOKEN_BINDING_FAILURE"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit
        val recovery = SabrPlaybackRecovery(store)

        assertEquals("retry_fresh_session", recovery.action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `protected no-media invalidates playback info and keeps the selected format`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "video:299:12 status=3 protected no-media"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 299, 0L)
        coEvery { store.recoverProtectedPlaybackInfo(holder) } returns Unit
        every { store.tokenSessionRenewing } returns false
        every { holder.sessionToken } returns "session"
        val recovery = SabrPlaybackRecovery(store)

        assertEquals("retry_fresh_session", recovery.action(holder))
        assertEquals(emptyList<Int>(), recovery.retryVideoItags())
        coVerify(exactly = 1) { store.recoverProtectedPlaybackInfo(holder) }
    }

    @Test
    fun `attestation rejection refreshes context and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns
            "SABR error: SABR attestation required: status=3, policy=true"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 900_000L)
        coEvery { store.recoverProtectedPlaybackInfo(holder) } returns Unit
        every { store.tokenSessionRenewing } returns false
        every { holder.sessionToken } returns "session"

        assertEquals("retry_fresh_session", SabrPlaybackRecovery(store).action(holder))
        coVerify(exactly = 1) { store.recoverProtectedPlaybackInfo(holder) }
    }

    @Test
    fun `pending attestation without media refreshes context and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns
            "SABR attestation remained pending without media for 3 consecutive responses: parts=5, status=2"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 933_000L)
        coEvery { store.recoverProtectedPlaybackInfo(holder) } returns Unit
        every { store.tokenSessionRenewing } returns false
        every { holder.sessionToken } returns "session"

        assertEquals("retry_fresh_session", SabrPlaybackRecovery(store).action(holder))
        coVerify(exactly = 1) { store.recoverProtectedPlaybackInfo(holder) }
    }

    @Test
    fun `attestation waits while the token session is replaced then requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        var time = 0L
        every { holder.terminalFailure() } returns "SABR error: SABR attestation required: status=3, policy=true"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 69_343L)
        every { holder.sessionToken } returns "session"
        coEvery { store.recoverProtectedPlaybackInfo(holder) } returns Unit
        every { store.tokenSessionRenewing } returns true
        val recovery = SabrPlaybackRecovery(store, holdMs = 45_000L, now = { time })

        assertNull(recovery.action(holder))
        assertTrue(recovery.awaitsTokenSession(holder))
        time = 20_000L
        assertNull(recovery.action(holder))

        every { store.tokenSessionRenewing } returns false
        assertEquals("retry_fresh_session", recovery.action(holder))
        assertFalse(recovery.awaitsTokenSession(holder))
    }

    @Test
    fun `attestation stops waiting for a token session that takes too long`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        var time = 0L
        every { holder.terminalFailure() } returns "SABR error: SABR attestation required: status=3, policy=true"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 69_343L)
        every { holder.sessionToken } returns "session"
        coEvery { store.recoverProtectedPlaybackInfo(holder) } returns Unit
        every { store.tokenSessionRenewing } returns true
        val recovery = SabrPlaybackRecovery(store, holdMs = 45_000L, now = { time })

        assertNull(recovery.action(holder))
        time = 45_000L
        assertEquals("retry_fresh_session", recovery.action(holder))
    }
}
