package dev.typetype.server.services

import java.nio.ByteBuffer

internal object Mp4SegmentIndex {
    fun firstSegmentEnd(header: ByteArray, indexStart: Long): Long? {
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
        if (referenceCount == 0 || firstOffset < 0) return null
        val referencedSize = box.int.toLong() and 0x7fffffffL
        if (referencedSize == 0L) return null
        val firstStart = indexStart + boxSize + firstOffset
        return firstStart + referencedSize - 1
    }

    private const val BOX_HEADER = 8
    private const val SIDX = 0x73696478
}
