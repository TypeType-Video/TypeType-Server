package dev.typetype.server

import dev.typetype.server.services.Mp4SegmentIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class Mp4SegmentIndexTest {
    @Test
    fun `finds the end of the first segment after a version 0 sidx`() {
        val init = ByteArray(1000)
        val sidx = sidx(version = 0, firstOffset = 0, sizes = listOf(500_000, 600_000))
        val header = init + sidx

        assertEquals(1000L + sidx.size + 500_000 - 1, Mp4SegmentIndex.firstSegmentEnd(header, 1000))
    }

    @Test
    fun `adds the first offset of a version 1 sidx`() {
        val sidx = sidx(version = 1, firstOffset = 16, sizes = listOf(300_000))

        assertEquals(sidx.size + 16L + 300_000 - 1, Mp4SegmentIndex.firstSegmentEnd(sidx, 0))
    }

    @Test
    fun `ignores anything that is not a complete sidx`() {
        val sidx = sidx(version = 0, firstOffset = 0, sizes = listOf(500_000))

        assertNull(Mp4SegmentIndex.firstSegmentEnd(ByteArray(64), 0))
        assertNull(Mp4SegmentIndex.firstSegmentEnd(sidx.copyOf(sidx.size - 4), 0))
        assertNull(Mp4SegmentIndex.firstSegmentEnd(sidx, 10_000))
    }

    private fun sidx(version: Int, firstOffset: Long, sizes: List<Int>): ByteArray {
        val timing = if (version == 0) 8 else 16
        val size = 8 + 4 + 8 + timing + 4 + sizes.size * 12
        val buffer = ByteBuffer.allocate(size)
        buffer.putInt(size).put("sidx".toByteArray())
        buffer.put(version.toByte()).put(ByteArray(3))
        buffer.putInt(1).putInt(1000)
        if (version == 0) buffer.putInt(0).putInt(firstOffset.toInt()) else buffer.putLong(0).putLong(firstOffset)
        buffer.putShort(0).putShort(sizes.size.toShort())
        sizes.forEach { buffer.putInt(it).putInt(5000).putInt(0x90000000.toInt()) }
        return buffer.array()
    }
}
