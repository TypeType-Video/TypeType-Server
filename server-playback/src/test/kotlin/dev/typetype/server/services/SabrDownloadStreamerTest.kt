package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrStreamState

class SabrDownloadRangeTest {
    @Test
    fun `adjacent parts share an exclusive segment boundary`() {
        val state = mockk<YoutubeSabrStreamState>()
        val format = format(durationMs = 1_000L)
        every { state.getEndSegment(format) } returns 0L
        every { state.getSegmentNumberAtOrAfterTimeMs(format, 250L) } returns 26
        every { state.getSegmentNumberAtOrAfterTimeMs(format, 500L) } returns 51

        val first = SabrDownloadRange(part = 0, parts = 4)
        val second = SabrDownloadRange(part = 1, parts = 4)

        assertEquals(1, first.startSequence(state, format))
        assertEquals(26, first.endSequenceExclusive(state, format))
        assertEquals(26, second.startSequence(state, format))
        assertEquals(51, second.endSequenceExclusive(state, format))
    }

    @Test
    fun `parts split the known segments evenly even when the time index is wrong`() {
        val state = mockk<YoutubeSabrStreamState>()
        val format = format(durationMs = 300_000L)
        every { state.getEndSegment(format) } returns 30L
        every { state.getSegmentNumberAtOrAfterTimeMs(format, any()) } returns 1

        val ranges = (0 until 4).map { SabrDownloadRange(part = it, parts = 4) }
        val bounds = ranges.map { it.startSequence(state, format) to it.endSequenceExclusive(state, format) }

        assertEquals(listOf(1 to 8, 8 to 16, 16 to 23, 23 to 31), bounds)
    }

    @Test
    fun `single part keeps natural stream completion`() {
        val state = mockk<YoutubeSabrStreamState>()
        val format = format(durationMs = 0L)
        val range = SabrDownloadRange()

        assertEquals(1, range.startSequence(state, format))
        assertNull(range.endSequenceExclusive(state, format))
    }

    @Test
    fun `part start time follows selected duration`() {
        val audio = format(durationMs = 1_800_000L)
        val video = format(durationMs = 900_000L)
        val range = SabrDownloadRange(part = 2, parts = 4)

        assertEquals(900_000L, range.startTimeMs(audio, video, audioOnly = true))
        assertEquals(900_000L, range.startTimeMs(audio, video, audioOnly = false))
    }

    @Test
    fun `part count is bounded`() {
        SabrDownloadRange(part = 11, parts = 12)
        assertThrows(IllegalArgumentException::class.java) {
            SabrDownloadRange(part = 0, parts = 13)
        }
    }

    private fun format(durationMs: Long): YoutubeSabrFormat = mockk {
        every { approxDurationMs } returns durationMs
    }
}
