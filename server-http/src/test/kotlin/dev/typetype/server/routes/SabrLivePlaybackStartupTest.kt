package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SabrLivePlaybackStartupTest {
    @Test
    fun `active live startup responds after the first media pair`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { session.isLive } returns true
        every { streamState.isLive } returns true
        every { streamState.liveHeadTimeMs } returns 120_000L
        every { streamState.getSegmentNumberAtOrAfterTimeMs(any(), any()) } returns 50
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            if (request.sequenceNumber in 50..52) {
                cached(request.format.itag, request.sequenceNumber, 100_000L + (request.sequenceNumber - 50) * 2_000L)
            } else {
                null
            }
        }

        val builder = SabrPlaybackWindowBuilder(store)
        val request = SabrPlaybackWindowRequest(0L, 100_000L, 299, 140, bufferGoalMs = 8_000L)
        val startup = builder.build(holder, request)
        val continuation = builder.build(
            holder,
            request.copy(
                bufferedRanges = listOf(
                    SabrPlaybackBufferedRange(140, 0L, 100_000L),
                    SabrPlaybackBufferedRange(299, 0L, 100_000L),
                ),
            ),
        )

        assertTrue(startup.isReady)
        assertTrue(continuation.isReady)
        assertEquals(1, startup.response.audio.segments.size)
        assertEquals(1, requireNotNull(startup.response.video).segments.size)
        assertEquals(1, continuation.response.audio.segments.size)
        assertEquals(1, requireNotNull(continuation.response.video).segments.size)
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
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { this@mockk.isAudio } returns isAudio
        every { mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { approxDurationMs } returns 900_000L
    }

    private fun cached(itag: Int, sequence: Int, startMs: Long): CachedSabrSegment = CachedSabrSegment(
        itag = itag,
        sequence = sequence,
        init = false,
        startMs = startMs,
        durationMs = 2_000L,
        mimeType = if (itag == 140) "audio/mp4" else "video/mp4",
        bytesBase64 = "AA==",
        byteLength = 1,
    )
}
