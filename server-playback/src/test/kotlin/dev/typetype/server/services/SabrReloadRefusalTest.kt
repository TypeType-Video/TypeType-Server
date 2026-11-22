package dev.typetype.server.services

import dev.typetype.server.sabr.SabrReloadException
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class SabrReloadRefusalTest {
    @Test
    fun `a refused player reload ends the session as an upstream refusal`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            every { session.streamState } returns mockk(relaxed = true)
            every { session.getCachedSegment(any()) } returns null
            val refusal = SabrReloadException(
                "SABR player reload 1/2 refused: Player response has no streamingData for MWEB " +
                    "(UNPLAYABLE: This content isn't available.)",
                "token",
            )
            val request = SabrSegmentRequest.media(video, 1)
            every { session.pumpOnceStreaming(any()) } throws refusal
            every { session.pumpOnceStreamingForDemand(any(), any()) } throws refusal
            val holder = SabrSessionHolder(
                session = session,
                info = mockk<YoutubeSabrInfo>(),
                audioFormat = audio,
                videoFormat = video,
                sessionToken = "session-token",
                key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
                lastRequestAt = Instant.EPOCH,
            )
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPumpLoop().run({ rounds++ < 3 }, holder, intervalMs = 0L)

            assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
            assertEquals(SabrFailureKind.UPSTREAM_REFUSED, holder.playbackStatus.terminalFailureKind())
            assertEquals(
                "SABR player reload 1/2 refused: Player response has no streamingData for MWEB " +
                    "(UNPLAYABLE: This content isn't available.)",
                holder.terminalFailure(),
            )
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.audioTrackId } returns null
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        every { format.lastModified } returns 0L
        every { format.xtags } returns ""
        return format
    }
}
