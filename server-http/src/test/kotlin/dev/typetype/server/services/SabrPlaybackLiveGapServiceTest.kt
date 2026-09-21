package dev.typetype.server.services

import dev.typetype.server.routes.SabrPlaybackRecovery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrPlaybackInitializationFailureTest {
    @Test
    fun `initialization timeout becomes a recoverable terminal failure`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(271, isAudio = false)
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, token())
        val holder = holder(audio, video)
        val store = mockk<SabrSessionStore>()
        every {
            store.getOrCreate(
                "video",
                "user",
                info,
                audio,
                video,
                prepared.initialToken,
                475_058L,
                false,
                SabrSessionPurpose.PLAYBACK,
                false,
                0L,
            )
        } returns holder
        coEvery { store.fetchInitializationData(holder, video) } coAnswers {
            delay(Long.MAX_VALUE)
            null
        }
        coEvery { store.fetchInitializationData(holder, audio) } coAnswers {
            delay(Long.MAX_VALUE)
            null
        }
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit

        val result = SabrPlaybackSessionService(store).prepare(
            "video",
            "user",
            prepared,
            audio,
            video,
            475_058L,
        )

        assertFalse(result.ready)
        assertEquals(6_000L, testScheduler.currentTime)
        assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
        assertEquals("retry_fresh_session", SabrPlaybackRecovery(store).action(holder))
        verify(exactly = 0) { store.startPump(any()) }
        coVerify(exactly = 1) { store.fetchInitializationData(holder, audio) }
    }

    @Test
    fun `missing required initialization terminates before starting pump`() = runBlocking {
        val audio = format(140, isAudio = true)
        val video = format(271, isAudio = false)
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, token())
        val holder = holder(audio, video)
        val store = mockk<SabrSessionStore>()
        every {
            store.getOrCreate(
                "video",
                "user",
                info,
                audio,
                video,
                prepared.initialToken,
                475_058L,
                false,
                SabrSessionPurpose.PLAYBACK,
                false,
                0L,
            )
        } returns holder
        coEvery { store.fetchInitializationData(holder, video) } returns null
        coEvery { store.fetchInitializationData(holder, audio) } returns byteArrayOf(1)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit

        val result = SabrPlaybackSessionService(store).prepare(
            "video",
            "user",
            prepared,
            audio,
            video,
            475_058L,
        )

        assertFalse(result.ready)
        assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
        assertEquals(
            "$SABR_RECOVERABLE_FAILURE_PREFIX SABR initialization unavailable for video:271",
            holder.terminalFailure(),
        )
        assertEquals("retry_fresh_session", SabrPlaybackRecovery(store).action(holder))
        verify(exactly = 0) { store.startPump(any()) }
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
        coVerify(exactly = 1) { store.fetchInitializationData(holder, video) }
        coVerify(exactly = 1) { store.fetchInitializationData(holder, audio) }
    }

    @Test
    fun `audio only preparation does not require video initialization`() = runBlocking {
        val audio = format(140, isAudio = true)
        val video = format(271, isAudio = false)
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, token())
        val holder = holder(audio, video)
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
                true,
                0L,
            )
        } returns holder
        coEvery { store.fetchInitializationData(holder, video) } returns null
        coEvery { store.fetchInitializationData(holder, audio) } returns byteArrayOf(1)
        every { store.startPump(holder) } returns Unit

        SabrPlaybackSessionService(store).prepare(
            "video",
            "user",
            prepared,
            audio,
            video,
            0L,
            audioOnly = true,
        )

        assertEquals(null, holder.terminalFailure())
        verify(exactly = 1) { store.startPump(holder) }
        coVerify(exactly = 0) { store.fetchInitializationData(holder, video) }
        coVerify(exactly = 1) { store.fetchInitializationData(holder, audio) }
    }

    private fun holder(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.getCachedSegment(any()) } returns null
        every { session.isBeyondEnd(any()) } returns false
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.setSelectVideoFormatBeforeAudio(any()) } returns Unit
        every { state.getMinBufferedEndMs() } returns 0L
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.audioTrackId } returns null
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        return format
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
