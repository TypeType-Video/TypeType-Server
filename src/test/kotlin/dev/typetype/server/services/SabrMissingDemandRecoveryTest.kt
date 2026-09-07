package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrMissingDemandRecoveryTest {
    @Test
    fun `missing near edge audio segment readvertises target track`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, isAudio = true)
            val video = format(299, isAudio = false)
            val request = SabrSegmentRequest.media(audio, 44)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            val result = mockk<YoutubeSabrSession.DemandResponseResult>()
            every { session.streamState } returns streamState
            every { session.getCachedSegment(any()) } returns null
            every { session.requestNumber } returns 25
            every { streamState.getMinBufferedEndMs() } returns 416_100L
            every { streamState.getSegmentStartMs(audio, 44) } returns 429_337L
            every { result.segmentCount } returns 2
            every { result.targetTrackSegmentCount } returns 1
            every { session.pumpOnceStreamingForDemand(any(), request) } returns result
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ == 0 }, holder, intervalMs = 100L)

            verify(exactly = 1) { session.prepareForMissingSegment(request) }
            assertEquals("140:44", holder.pendingSegmentDemandSummary())
            assertEquals(0L, testScheduler.currentTime)
        } finally {
            SabrSegmentDemandTracker.clearAll()
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
        sessionToken = "session-token",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        every { format.lastModified } returns itag.toLong()
        every { format.xtags } returns null
        return format
    }
}
