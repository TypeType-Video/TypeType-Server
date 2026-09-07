package dev.typetype.server.sabr

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange as PipeRange
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat as PipeFormat

class SabrBoundaryContractTest {
    @Test
    fun `format exposes the current extractor contract without loss`() {
        val pipe = mockk<PipeFormat>(relaxed = true)
        every { pipe.isAudio } returns false
        every { pipe.isVideo } returns true
        every { pipe.itag } returns 137
        every { pipe.width } returns 1920
        every { pipe.height } returns 1080
        every { pipe.bitrate } returns 4_000_000
        every { pipe.mimeType } returns "video/mp4"
        every { pipe.initializationUrl } returns "https://example.test/init"

        val format = YoutubeSabrFormat(pipe)

        assertFalse(format.isAudio)
        assertTrue(format.isVideo)
        assertEquals(137, format.itag)
        assertEquals(1920, format.width)
        assertEquals(1080, format.height)
        assertEquals(4_000_000, format.bitrate)
        assertEquals("video/mp4", format.mimeType)
        assertEquals("https://example.test/init", format.initializationUrl)
    }

    @Test
    fun `buffered range preserves timing and segment identity`() {
        val pipe = mockk<PipeRange>(relaxed = true)
        every { pipe.itag } returns 137
        every { pipe.lastModified } returns 42L
        every { pipe.xtags } returns "xtags"
        every { pipe.startTimeMs } returns 1_000L
        every { pipe.durationMs } returns 4_000L
        every { pipe.startSegmentIndex } returns 3
        every { pipe.endSegmentIndex } returns 7
        every { pipe.timescale } returns 1_000

        val range = SabrBufferedRange.fromDelegate(pipe)

        assertEquals(137, range.itag)
        assertEquals(42L, range.lastModified)
        assertEquals("xtags", range.xtags)
        assertEquals(1_000L, range.startTimeMs)
        assertEquals(4_000L, range.durationMs)
        assertEquals(3, range.startSegmentIndex)
        assertEquals(7, range.endSegmentIndex)
        assertEquals(1_000, range.timescale)
    }
}
