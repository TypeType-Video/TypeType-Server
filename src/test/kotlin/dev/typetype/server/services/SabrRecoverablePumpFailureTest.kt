package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrRecoverableException
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrRecoverablePumpFailureTest {
    @Test
    fun `recoverable media failure requests a fresh session`() = runTest {
        val holder = pumpFailure("Unexpected EOF")

        assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
        assertTrue(holder.terminalFailure().orEmpty().startsWith(SABR_RECOVERABLE_FAILURE_PREFIX))
    }

    @Test
    fun `local spool failure remains terminal without fresh session recovery`() = runTest {
        val holder = pumpFailure("Could not write SABR spool file")

        assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
        assertEquals("Could not write SABR spool file", holder.terminalFailure())
    }

    private suspend fun pumpFailure(message: String): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.requestNumber } returns 1
        every { session.isComplete } returns false
        every { session.cachedBytes } returns 0L
        every { streamState.nextRequestPolicy } returns null
        every { session.pumpOnceStreaming(any()) } throws SabrRecoverableException(message)
        val holder = holder(session)

        SabrSessionPump().pumpLoop({ true }, holder, intervalMs = 0L)
        return holder
    }

    private fun holder(session: YoutubeSabrSession): SabrSessionHolder = SabrSessionHolder(
        session = session,
        info = mockk<YoutubeSabrInfo>(),
        audioFormat = format(140, true),
        videoFormat = format(137, false),
        sessionToken = "session-token",
        key = SabrSessionKey("video", "user", 140, null, 137, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        return format
    }
}
