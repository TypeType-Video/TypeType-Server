package dev.typetype.server.routes

import dev.typetype.server.services.SabrDownloadRange
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException

class SabrDownloadFailureRecoveryTest {
    private val store = mockk<SabrSessionStore>(relaxed = true)
    private val holder = mockk<SabrSessionHolder> {
        every { key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
    }
    private val recovery = SabrDownloadFailureRecovery(store)

    @Test
    fun `an attestation demand repairs the protected session for the next attempt`() = runBlocking {
        recovery.recover(
            holder,
            SabrDownloadRange(part = 1, parts = 2),
            IOException("SABR error: SABR attestation required: parts=3, status=3"),
        )

        coVerify(exactly = 1) { store.recoverProtectedPlaybackInfo(holder) }
        coVerify(exactly = 0) { store.invalidatePlaybackInfo(any()) }
    }

    @Test
    fun `any other failure asks for fresh playback info`() = runBlocking {
        recovery.recover(holder, SabrDownloadRange(), IOException("SABR download stalled without receiving media"))

        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
        coVerify(exactly = 0) { store.recoverProtectedPlaybackInfo(any()) }
    }

    @Test
    fun `a failing repair never escapes the download response`() = runBlocking {
        coEvery { store.invalidatePlaybackInfo(any()) } throws IllegalStateException("cache down")

        recovery.recover(holder, SabrDownloadRange(), IOException("SABR download upstream pump timed out"))
    }
}
