package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrVodRoundedBoundaryWindowTest {
    @Test
    fun `buffered end on a rounded index boundary asks for the following segment`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(299, isAudio = false)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { state.getSegmentNumberAtOrAfterTimeMs(video, 50_366L) } returns 10
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 59_907L) } returns 7
        val held = segment(video, sequence = 10, startMs = 45_366L, durationMs = 5_000L)
        val audioSegment = segment(audio, sequence = 7, startMs = 59_907L, durationMs = 9_985L)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns state
        every { session.getCachedSegment(any()) } answers { held.takeIf { firstArg<SabrSegmentRequest>().isVideo(10) } }
        every { session.getReadableSegment(any()) } answers {
            val request = firstArg<SabrSegmentRequest>()
            when {
                request.isVideo(10) -> held
                request.format.isAudio && request.sequenceNumber == 7 -> audioSegment
                else -> null
            }
        }
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } returns null

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(
                generation = 0L,
                playerTimeMs = 45_000L,
                videoItag = video.itag,
                audioItag = audio.itag,
                bufferGoalMs = 10_000L,
                bufferedRanges = listOf(
                    SabrPlaybackBufferedRange(video.itag, 0L, 50_366L),
                    SabrPlaybackBufferedRange(audio.itag, 0L, 59_907L),
                ),
            ),
        )

        assertEquals(listOf(11), result.blockedRequests.filter { it.format == video }.map { it.sequenceNumber })
    }

    private fun SabrSegmentRequest.isVideo(sequence: Int): Boolean = !format.isAudio && sequenceNumber == sequence

    private fun segment(format: YoutubeSabrFormat, sequence: Int, startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.itag } returns format.itag
        every { header.isInitSegment } returns false
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        return mockk<SabrMediaSegment>().also {
            every { it.header } returns header
            every { it.isComplete } returns true
        }
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
        sessionToken = "session",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat = mockk<YoutubeSabrFormat>().also {
        every { it.itag } returns itag
        every { it.isAudio } returns isAudio
        every { it.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { it.approxDurationMs } returns 900_000L
    }
}
