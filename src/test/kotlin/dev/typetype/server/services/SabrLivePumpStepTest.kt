package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import java.time.Instant

class SabrLivePumpStepTest {
    @Test
    fun `live pump fills below read ahead cushion`() = runTest {
        val fixture = fixture(playerTimeMs = 100_000L, observedEndMs = 103_000L)
        var pumps = 0

        val immediate = pumpLiveReadAhead(fixture.holder, SabrPumpRuntime(), { pumps++; 0 }) { _, _ -> }

        assertTrue(pumps == 1)
        assertFalse(immediate)
    }

    @Test
    fun `live pump idles above read ahead cushion`() = runTest {
        val fixture = fixture(playerTimeMs = 100_000L, observedEndMs = 130_000L)
        var pumps = 0

        val immediate = pumpLiveReadAhead(fixture.holder, SabrPumpRuntime(), { pumps++; 0 }) { _, _ -> }

        assertTrue(pumps == 0)
        assertFalse(immediate)
    }

    private fun fixture(playerTimeMs: Long, observedEndMs: Long): Fixture {
        val audio = format(140, true)
        val video = format(299, false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns mockk(relaxed = true)
        every { session.getCachedSegment(any()) } returns null
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
        holder.setPlayerTimeMs(playerTimeMs)
        holder.observeMediaSegment(segment(audio.itag, observedEndMs - SEGMENT_DURATION_MS))
        holder.observeMediaSegment(segment(video.itag, observedEndMs - SEGMENT_DURATION_MS))
        return Fixture(holder)
    }

    private fun format(itag: Int, audio: Boolean): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { isAudio } returns audio
        every { bitrate } returns if (audio) 128_000 else 2_000_000
        every { lastModified } returns 1L
        every { xtags } returns null
    }

    private fun segment(itag: Int, startMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader> {
            every { this@mockk.itag } returns itag
            every { sequenceNumber } returns 100
            every { this@mockk.startMs } returns startMs
            every { durationMs } returns SEGMENT_DURATION_MS
            every { isInitSegment } returns false
        }
        return mockk { every { this@mockk.header } returns header }
    }

    private data class Fixture(val holder: SabrSessionHolder)

    private companion object {
        const val SEGMENT_DURATION_MS = 2_000L
    }
}
