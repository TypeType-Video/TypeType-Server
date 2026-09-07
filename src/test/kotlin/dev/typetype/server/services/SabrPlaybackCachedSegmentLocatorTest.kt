package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState

class SabrPlaybackCachedSegmentLocatorTest {
    @Test
    fun `finds the cached live segment covering a rounded audio boundary`() = runTest {
        val store = mockk<SabrSessionStore>()
        val holder = mockk<SabrSessionHolder>()
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { format.itag } returns 140
        every { holder.observedMediaSegment(format) } returns null
        every { holder.session } returns session
        every { session.streamState } returns state
        every { state.getSegmentStartMs(format, any()) } returns 0L
        every { state.getSegmentEndMs(format, any()) } returns 2_000L
        coEvery { store.cachedSegment(holder, any()) } answers {
            val sequence = secondArg<SabrSegmentRequest>().sequenceNumber
            when (sequence) {
                1841446 -> cached(sequence, 3_682_888_446L)
                1841447 -> cached(sequence, 3_682_890_443L)
                else -> null
            }
        }

        val segment = store.findCachedPlaybackMediaAt(
            holder,
            format,
            targetMs = 3_682_888_446L,
            predictedSequence = 1841447,
        )

        assertEquals(1841446, segment?.sequence)
    }

    @Test
    fun `finds cached audio after a rounded multi-sequence live gap`() = runTest {
        val store = mockk<SabrSessionStore>()
        val holder = mockk<SabrSessionHolder>()
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { format.itag } returns 140
        every { holder.observedMediaSegment(format) } returns null
        every { holder.session } returns session
        every { session.streamState } returns state
        every { state.getSegmentStartMs(format, any()) } returns 0L
        every { state.getSegmentEndMs(format, any()) } returns 5_000L
        coEvery { store.cachedSegment(holder, any()) } answers {
            secondArg<SabrSegmentRequest>().sequenceNumber
                .takeIf { it == 3_980 }
                ?.let { cached(it, 19_900_081L) }
        }

        val segment = store.findCachedPlaybackMediaAt(
            holder,
            format,
            targetMs = 19_865_073L,
            predictedSequence = 3_973,
            allowFollowing = true,
        )

        assertEquals(3_980, segment?.sequence)
    }

    @Test
    fun `uses the same live timing tolerance as demand resolution`() = runTest {
        val store = mockk<SabrSessionStore>()
        val holder = mockk<SabrSessionHolder>()
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { format.itag } returns 140
        every { holder.observedMediaSegment(format) } returns null
        every { holder.session } returns session
        every { session.streamState } returns state
        every { state.getSegmentStartMs(format, any()) } returns 0L
        every { state.getSegmentEndMs(format, any()) } returns 5_000L
        coEvery { store.cachedSegment(holder, any()) } answers {
            secondArg<SabrSegmentRequest>().sequenceNumber
                .takeIf { it == 5_947 }
                ?.let { cached(it, 29_735_079L) }
        }

        val segment = store.findCachedPlaybackMediaAt(
            holder,
            format,
            targetMs = 29_720_066L,
            predictedSequence = 5_944,
            allowFollowing = true,
        )

        assertEquals(5_947, segment?.sequence)
    }

    @Test
    fun `finds cached audio slightly after a video-aligned live start`() = runTest {
        val store = mockk<SabrSessionStore>()
        val holder = mockk<SabrSessionHolder>()
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { format.itag } returns 140
        every { holder.observedMediaSegment(format) } returns null
        every { holder.session } returns session
        every { session.streamState } returns state
        every { state.getSegmentStartMs(format, any()) } returns 0L
        every { state.getSegmentEndMs(format, any()) } returns 2_000L
        coEvery { store.cachedSegment(holder, any()) } answers {
            when (val sequence = secondArg<SabrSegmentRequest>().sequenceNumber) {
                100 -> cached(sequence, 100_007L)
                104 -> cached(sequence, 108_000L)
                else -> null
            }
        }

        val segment = store.findCachedPlaybackMediaAt(
            holder,
            format,
            targetMs = 100_000L,
            predictedSequence = 110,
            allowFollowing = true,
        )

        assertEquals(100, segment?.sequence)
    }

    private fun cached(sequence: Int, startMs: Long): CachedSabrSegment = CachedSabrSegment(
        itag = 140,
        sequence = sequence,
        init = false,
        startMs = startMs,
        durationMs = -1L,
        mimeType = "audio/mp4",
        bytesBase64 = "AA==",
        byteLength = 1,
    )
}
