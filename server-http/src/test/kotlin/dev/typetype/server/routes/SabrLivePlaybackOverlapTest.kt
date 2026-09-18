package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrLivePlaybackOverlapTest {
    @Test
    fun `audio overlap still covers the ready horizon after player time`() = runTest {
        val audio = format(140, true)
        val video = format(137, false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.liveHeadTimeMs } returns 176_937_494L
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, any()) } returns 35_631
        every { state.getSegmentNumberAtOrAfterTimeMs(video, any()) } returns 35_632
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            val baseSequence = if (request.format.isAudio) 35_631 else 35_632
            val baseStartMs = if (request.format.isAudio) 176_907_865L else 176_912_802L
            request.sequenceNumber.takeIf { it in baseSequence..35_637 }?.let {
                cached(request.format.itag, it, baseStartMs + (it - baseSequence) * 4_938L)
            }
        }

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(
                generation = 0L,
                playerTimeMs = 176_917_494L,
                videoItag = 137,
                audioItag = 140,
                bufferGoalMs = 8_000L,
            ),
        )

        assertTrue(result.isReady)
        assertEquals(4, result.response.audio.segments.size)
        assertTrue(result.response.audio.segments.last().let { it.startMs + it.durationMs } >= 176_925_494L)
    }

    private fun holder(
        session: YoutubeSabrSession,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ) = SabrSessionHolder(
        session = session,
        info = mockk<YoutubeSabrInfo>(),
        audioFormat = audio,
        videoFormat = video,
        sessionToken = "session",
        key = SabrSessionKey("video", "user", 140, null, 137, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, audio: Boolean): YoutubeSabrFormat =
        mockk<YoutubeSabrFormat>().also {
            every { it.itag } returns itag
            every { it.isAudio } returns audio
            every { it.mimeType } returns if (audio) "audio/mp4" else "video/mp4"
            every { it.approxDurationMs } returns 200_000_000L
        }

    private fun cached(itag: Int, sequence: Int, startMs: Long) = CachedSabrSegment(
        itag = itag,
        sequence = sequence,
        init = false,
        startMs = startMs,
        durationMs = 4_938L,
        mimeType = if (itag == 140) "audio/mp4" else "video/mp4",
        bytesBase64 = "AA==",
        byteLength = 1,
    )
}
