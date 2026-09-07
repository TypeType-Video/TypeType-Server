package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant
import java.util.Base64

class SabrPlaybackLiveGapServiceTest {
    @Test
    fun `serves the next live segment when YouTube skips a sequence`() = runTest {
        val video = format(299, isAudio = false)
        val audio = format(140, isAudio = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.getSegmentStartMs(video, 92) } returns 475_000L
        val holder = holder(session, audio, video)
        val replacement = cachedSegment(299, 93, 480_000L, byteArrayOf(1, 2, 3))
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            secondArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 93 }?.let { replacement }
        }
        every { store.requestSegmentDemand(holder, any(), 0L) } returns Unit

        val result = SabrPlaybackSessionService(store).fetchMedia(
            holder = holder,
            format = video,
            sequence = 92,
            timeoutMs = 1_000L,
            generation = 0L,
        ) as SabrPlaybackSegmentResult.Ready

        assertArrayEquals(byteArrayOf(1, 2, 3), result.bytes)
        assertEquals(93, holder.lastServedSequence(video))
    }

    @Test
    fun `rejects a following live segment outside the recoverable window`() = runTest {
        val video = format(299, isAudio = false)
        val audio = format(140, isAudio = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.liveHeadTimeMs } returns 31_680_059L
        every { state.getSegmentStartMs(video, 6_320) } returns 31_600_059L
        every { state.getSegmentEndMs(video, 6_320) } returns 31_605_059L
        val holder = holder(session, audio, video)
        holder.markExpectedLive()
        holder.setLastServedSequence(video.itag, 6_319)
        val replacement = cachedSegment(299, 6_335, 31_675_059L, byteArrayOf(1, 2, 3))
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            secondArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 6_335 }?.let { replacement }
        }
        every { store.requestSegmentDemand(holder, any(), 0L) } returns Unit

        val result = SabrPlaybackSessionService(store).fetchMedia(
            holder = holder,
            format = video,
            sequence = 6_320,
            timeoutMs = 1_000L,
            generation = 0L,
        )

        assertEquals(SabrPlaybackSegmentResult.Retry(holder, "repositioning"), result)
        assertEquals(6_319, holder.lastServedSequence(video))
        assertTrue(holder.terminalFailure().orEmpty().contains("live 299 media discontinuity"))
    }

    private fun holder(
        session: YoutubeSabrSession,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ): SabrSessionHolder = SabrSessionHolder(
        session = session,
        info = mockk<YoutubeSabrInfo>(),
        audioFormat = audio,
        videoFormat = video,
        sessionToken = "session-token",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>(relaxed = true)
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        return format
    }

    private fun cachedSegment(
        itag: Int,
        sequence: Int,
        startMs: Long,
        bytes: ByteArray,
    ): CachedSabrSegment = CachedSabrSegment(
        itag = itag,
        sequence = sequence,
        init = false,
        startMs = startMs,
        durationMs = 5_000L,
        mimeType = "video/mp4",
        bytesBase64 = Base64.getEncoder().encodeToString(bytes),
        byteLength = bytes.size,
    )
}
