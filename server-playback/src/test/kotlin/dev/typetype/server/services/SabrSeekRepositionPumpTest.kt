package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSeekDiagnosticsTest {
    @Test
    fun `seek generation clears diagnostics from the previous position`() {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val state = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns state
            every { session.getCachedSegment(any()) } returns null
            every { state.getSegmentNumberAtOrAfterTimeMs(any(), 120_000L) } returns 1
            every { state.getMinBufferedEndMs() } returns 0L
            val holder = holder(session, audio, video)
            SabrPlaybackDiagnostics.record(holder, SabrSegmentRequest.media(audio, 1), "status=2 policy=true")

            SabrPlaybackSessionService(mockk(relaxed = true)).seekExisting(holder, 120_000L)

            assertNull(SabrPlaybackDiagnostics.blocker(holder))
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
        every { format.audioTrackId } returns null
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        every { format.lastModified } returns 0L
        every { format.xtags } returns ""
        return format
    }
}
