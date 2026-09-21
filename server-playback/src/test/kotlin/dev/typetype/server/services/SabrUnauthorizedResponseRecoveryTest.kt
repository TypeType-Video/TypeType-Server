package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrTransitioningLivePlaybackTest {
    @Test
    fun `live protocol response overrides stale ended metadata`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, token(), isLive = false, isLiveContent = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { session.liveHeadSequenceNumber } returns 5_536L
        every { state.isLive } returns true
        every { state.isPostLiveDvr } returns false
        every { state.liveHeadTimeMs } returns 11_070_200L
        every { state.liveHeadSequenceNumber } returns 5_536L
        val holder = SabrSessionHolder(
            session = session,
            info = info,
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
        val store = mockk<SabrSessionStore>()
        every {
            store.getOrCreate(
                "video",
                "user",
                info,
                audio,
                video,
                prepared.initialToken,
                0L,
                false,
                SabrSessionPurpose.PLAYBACK,
                false,
                0L,
            )
        } returns holder
        coEvery { store.fetchInitializationData(holder, video) } returns null
        coEvery { store.fetchInitializationData(holder, audio) } returns null
        coEvery { store.ensureWarmed(holder, 8) } returns Unit
        every { store.startPump(holder) } returns Unit

        val result = SabrPlaybackSessionService(store).prepare("video", "user", prepared, audio, video, 0L)

        assertTrue(holder.expectsLive())
        assertTrue(result.startTimeMs > 0L)
        assertNull(holder.terminalFailure())
        assertEquals(11_050_200L, result.startTimeMs)
        coVerify(exactly = 1) { store.ensureWarmed(holder, 8) }
        verify(exactly = 1) { state.setPlayerTimeMs(9_007_199_254_740_991L) }
        verify(exactly = 1) { store.startPump(holder) }
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { this@mockk.isAudio } returns isAudio
        every { audioTrackId } returns null
        every { mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { bitrate } returns if (isAudio) 128_000 else 5_000_000
    }

    private fun token(): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "visitor-token",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "visitor-data",
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
