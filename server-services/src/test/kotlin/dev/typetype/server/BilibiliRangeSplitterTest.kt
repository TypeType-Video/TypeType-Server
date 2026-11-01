package dev.typetype.server

import dev.typetype.server.services.BilibiliRangeSplitter
import dev.typetype.server.services.MAX_BILIBILI_SUBCHUNK_SIZE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BilibiliRangeSplitterTest {

    @Test
    fun `parseRange parses standard range headers`() {
        assertEquals(Pair(0L, 100L), BilibiliRangeSplitter.parseRange("bytes=0-100"))
        assertEquals(Pair(1000L, 2500000L), BilibiliRangeSplitter.parseRange("bytes=1000-2500000"))
        assertEquals(Pair(500L, null), BilibiliRangeSplitter.parseRange("bytes=500-"))
        assertEquals(Pair(null, 500L), BilibiliRangeSplitter.parseRange("bytes=-500"))
    }

    @Test
    fun `parseRange handles whitespace and case insensitivity`() {
        assertEquals(Pair(0L, 500L), BilibiliRangeSplitter.parseRange("  BYTES=0-500  "))
        assertEquals(Pair(10L, 20L), BilibiliRangeSplitter.parseRange("bytes=  10 - 20  "))
    }

    @Test
    fun `parseRange rejects invalid formats`() {
        assertNull(BilibiliRangeSplitter.parseRange(null))
        assertNull(BilibiliRangeSplitter.parseRange(""))
        assertNull(BilibiliRangeSplitter.parseRange("invalid"))
        assertNull(BilibiliRangeSplitter.parseRange("bytes="))
        assertNull(BilibiliRangeSplitter.parseRange("bytes=0-10,20-30"))
        assertNull(BilibiliRangeSplitter.parseRange("items=0-100"))
    }

    @Test
    fun `splitRange keeps ranges under 1MB as single slice`() {
        val ranges = BilibiliRangeSplitter.splitRange(0, 500_000, MAX_BILIBILI_SUBCHUNK_SIZE)
        assertEquals(1, ranges.size)
        assertEquals(0L, ranges[0].start)
        assertEquals(500_000L, ranges[0].end)
        assertEquals(500_001L, ranges[0].length)
        assertTrue(ranges[0].length <= MAX_BILIBILI_SUBCHUNK_SIZE)
    }

    @Test
    fun `splitRange handles exact 1MB boundary`() {
        val ranges = BilibiliRangeSplitter.splitRange(0, MAX_BILIBILI_SUBCHUNK_SIZE - 1, MAX_BILIBILI_SUBCHUNK_SIZE)
        assertEquals(1, ranges.size)
        assertEquals(MAX_BILIBILI_SUBCHUNK_SIZE, ranges[0].length)
        assertTrue(ranges[0].length <= MAX_BILIBILI_SUBCHUNK_SIZE)
    }

    @Test
    fun `splitRange splits 2_5MB DASH segment into sub-1MB chunks`() {
        val start = 0L
        val end = 2_500_000L
        val ranges = BilibiliRangeSplitter.splitRange(start, end, MAX_BILIBILI_SUBCHUNK_SIZE)

        assertEquals(3, ranges.size)
        assertEquals(0L, ranges[0].start)
        assertEquals(999_999L, ranges[0].end)
        assertEquals(1_000_000L, ranges[1].start)
        assertEquals(1_999_999L, ranges[1].end)
        assertEquals(2_000_000L, ranges[2].start)
        assertEquals(2_500_000L, ranges[2].end)

        assertEquals(end - start + 1, ranges.sumOf { it.length })
        assertEquals(ranges[0].end + 1, ranges[1].start)
        assertEquals(ranges[1].end + 1, ranges[2].start)
    }

    @Test
    fun `splitRange preserves arbitrary non-zero offsets`() {
        val start = 12_345_678L
        val end = 14_845_677L
        val ranges = BilibiliRangeSplitter.splitRange(start, end, MAX_BILIBILI_SUBCHUNK_SIZE)

        assertEquals(3, ranges.size)
        assertEquals(start, ranges.first().start)
        assertEquals(end, ranges.last().end)
        assertEquals(end - start + 1, ranges.sumOf { it.length })
        ranges.forEach {
            assertTrue(it.length <= MAX_BILIBILI_SUBCHUNK_SIZE)
        }
    }

    @Test
    fun `splitRange validates inputs`() {
        assertThrows<IllegalArgumentException> { BilibiliRangeSplitter.splitRange(-1, 100) }
        assertThrows<IllegalArgumentException> { BilibiliRangeSplitter.splitRange(100, 50) }
        assertThrows<IllegalArgumentException> { BilibiliRangeSplitter.splitRange(0, 100, 0) }
    }

    @Test
    fun `parseContentRangeTotal extracts total length`() {
        assertEquals(52428800L, BilibiliRangeSplitter.parseContentRangeTotal("bytes 0-999999/52428800"))
        assertEquals(100L, BilibiliRangeSplitter.parseContentRangeTotal("bytes 0-10/100"))
        assertNull(BilibiliRangeSplitter.parseContentRangeTotal("bytes 0-999999/*"))
        assertNull(BilibiliRangeSplitter.parseContentRangeTotal("invalid"))
        assertNull(BilibiliRangeSplitter.parseContentRangeTotal(null))
    }
}
