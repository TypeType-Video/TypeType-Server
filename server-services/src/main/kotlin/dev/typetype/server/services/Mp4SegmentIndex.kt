package dev.typetype.server.services

import java.nio.ByteBuffer

internal object Mp4SegmentIndex {
    fun firstSegmentEnd(header: ByteArray, indexStart: Long): Long? = segments(header, indexStart)?.first()?.last

    fun segments(header: ByteArray, indexStart: Long): List<LongRange>? {
        if (indexStart < 0 || indexStart + BOX_HEADER > header.size) return null
        val box = ByteBuffer.wrap(header, indexStart.toInt(), header.size - indexStart.toInt())
        val boxSize = box.int.toLong() and 0xffffffffL
        if (box.int != SIDX) return null
        if (boxSize < BOX_HEADER || indexStart + boxSize > header.size) return null
        val version = box.get().toInt()
        box.position(box.position() + 3 + 4 + 4)
        val firstOffset = if (version == 0) {
            box.int
            box.int.toLong() and 0xffffffffL
        } else {
            box.long
            box.long
        }
        box.short
        val referenceCount = box.short.toInt() and 0xffff
        if (referenceCount == 0 || firstOffset < 0 || box.remaining() < referenceCount * REFERENCE_BYTES) return null
        var start = indexStart + boxSize + firstOffset
        val segments = ArrayList<LongRange>(referenceCount)
        repeat(referenceCount) {
            val reference = box.int
            if (reference < 0) return null
            val size = reference.toLong()
            if (size == 0L) return null
            segments += start until start + size
            start += size
            box.position(box.position() + 8)
        }
        return segments
    }

    private const val BOX_HEADER = 8
    private const val REFERENCE_BYTES = 12
    private const val SIDX = 0x73696478
}
