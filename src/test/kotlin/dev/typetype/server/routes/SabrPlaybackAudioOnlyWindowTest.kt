package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrPlaybackAudioOnlyWindowTest {
    @Test
    fun `audio only window never waits for video segments`() = runTest {
        val audio = format(140, true)
        val video = format(137, false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, 79_000L) } returns 8
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "audio-session",
            key = SabrSessionKey("video", "user", 140, null, 137, 0L),
            lastRequestAt = Instant.EPOCH,
        )
        val store = mockk<SabrSessionStore>()
        val requestedItags = mutableListOf<Int>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            requestedItags += request.format.itag
            if (request.format.itag == 140 && request.sequenceNumber == 8) {
                cached(sequence = 8, startMs = 79_000L, durationMs = 10_000L)
            } else {
                null
            }
        }

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(
                generation = 0L,
                playerTimeMs = 79_000L,
                videoItag = 137,
                audioItag = 140,
                bufferGoalMs = 1_000L,
                audioOnly = true,
            ),
        )

        assertTrue(result.isReady)
        assertNull(result.response.video)
        assertEquals(8, result.response.audio.segments.single().url.sequenceFromUrl())
        assertFalse(requestedItags.contains(137))
        coVerify(exactly = 1) { store.cachedSegment(holder, any()) }
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { format.approxDurationMs } returns 420_000L
        return format
    }

    private fun cached(sequence: Int, startMs: Long, durationMs: Long): CachedSabrSegment = CachedSabrSegment(
        itag = 140,
        sequence = sequence,
        init = false,
        startMs = startMs,
        durationMs = durationMs,
        mimeType = "audio/mp4",
        bytesBase64 = "AA==",
        byteLength = 1,
    )

    private fun String.sequenceFromUrl(): Int = substringAfter("/segment/").substringBefore('?').toInt()
}
